package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Métricas comerciales basadas en ventas confirmadas y sus costos históricos registrados. */
public final class AnalisisAvanzadoServicio {
    public record Indicadores(long cantidadVentas,BigDecimal facturacion,BigDecimal ticketPromedio,
         long unidadesVendidas,long unidadesConCosto,BigDecimal ingresosConCosto,
         BigDecimal costosHistoricos,BigDecimal gananciaBrutaConocida) {
        public BigDecimal margenConocidoPorcentaje() {
            return ReglasAnalisis.porcentaje(gananciaBrutaConocida,ingresosConCosto);
        }
        public BigDecimal coberturaCostosPorcentaje() {
            return ReglasAnalisis.porcentaje(BigDecimal.valueOf(unidadesConCosto),
                BigDecimal.valueOf(unidadesVendidas));
        }
        public boolean costoCompleto() {return unidadesVendidas>0 && unidadesVendidas==unidadesConCosto;}
    }
    public record Dia(LocalDate fecha,long ventas,BigDecimal facturacion) {}
    public record Hora(int hora,long ventas,BigDecimal facturacion) {}
    public record Medio(String medio,long operaciones,BigDecimal facturacion) {}
    public record Vendedor(String nombre,long ventas,BigDecimal facturacion) {}
    public record ProductoFila(String codigo,String nombre,boolean activo,int stock,Integer stockMinimo,
        long unidades,BigDecimal facturacion,long unidadesConCosto,BigDecimal ingresosConCosto,
        BigDecimal gananciaBrutaConocida,BigDecimal diasCobertura,String diagnostico) {
        public BigDecimal margenConocido() {
            return ReglasAnalisis.porcentaje(gananciaBrutaConocida,ingresosConCosto);
        }
        public boolean costoCompleto(){return unidades>0 && unidades==unidadesConCosto;}
    }
    public record Informe(LocalDate desde,LocalDate hasta,Indicadores indicadores,
         List<Dia> dias,List<Hora> horas,List<Medio> medios,List<ProductoFila> productos,
         List<Vendedor> vendedores) {
        public List<ProductoFila> oportunidades() {
            return productos.stream().filter(p->p.activo() && !"SIN ALERTAS".equals(p.diagnostico()))
                .sorted(Comparator.comparingInt((ProductoFila p)->ReglasAnalisis.prioridad(p.diagnostico()))
                    .thenComparing(Comparator.comparingLong(ProductoFila::unidades).reversed())
                    .thenComparing(ProductoFila::nombre))
                .toList();
        }
        public Hora horaPico() {
            // Hora pico = más operaciones; en empate, mayor facturación y hora más temprana.
            return horas.stream().max(Comparator.comparingLong(Hora::ventas)
                .thenComparing(Hora::facturacion).thenComparingInt(h->-h.hora())).orElse(null);
        }
    }
    private static final BigDecimal CERO=new BigDecimal("0.00");

    public static void validarPeriodo(LocalDate desde,LocalDate hasta){
        if(desde==null || hasta==null || hasta.isBefore(desde))
            throw new IllegalArgumentException("El período de análisis es inválido");
        if(ChronoUnit.DAYS.between(desde,hasta)>365)
            throw new IllegalArgumentException("Seleccioná un período de hasta 366 días");
    }
    public Informe consultar(LocalDate desde,LocalDate hasta) throws SQLException {
        validarPeriodo(desde,hasta);
        Timestamp inicio=Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin=Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
        int dias=(int)ChronoUnit.DAYS.between(desde,hasta)+1;
        try(Connection c=Conexion.abrir()){
            // Repeatable-read: todos los gráficos se calculan sobre la misma instantánea.
            c.setReadOnly(true);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            try{
                Indicadores kpi=indicadores(c,inicio,fin);
                List<Dia> porDia=dias(c,inicio,fin,desde,hasta);
                List<Hora> porHora=horas(c,inicio,fin);
                List<Medio> porMedio=medios(c,inicio,fin);
                List<ProductoFila> porProducto=productos(c,inicio,fin,dias);
                List<Vendedor> porVendedor=vendedores(c,inicio,fin);
                c.commit();
                return new Informe(desde,hasta,kpi,List.copyOf(porDia),List.copyOf(porHora),
                    List.copyOf(porMedio),List.copyOf(porProducto),List.copyOf(porVendedor));
            }catch(SQLException | RuntimeException e){c.rollback();throw e;}
        }
    }
    private static void rango(PreparedStatement p,Timestamp inicio,Timestamp fin)throws SQLException {
        p.setTimestamp(1,inicio);p.setTimestamp(2,fin);
    }
    private static BigDecimal dinero(ResultSet r,int i)throws SQLException {
        BigDecimal v=r.getBigDecimal(i);return v==null?CERO:v;
    }
    private static Indicadores indicadores(Connection c,Timestamp inicio,Timestamp fin)throws SQLException {
        long operaciones;BigDecimal facturacion,ticket;
        try(PreparedStatement p=c.prepareStatement(
            "SELECT COUNT(*),COALESCE(SUM(total),0),COALESCE(AVG(total),0) FROM Venta " +
            "WHERE fecha_hora>=? AND fecha_hora<?")) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                r.next();operaciones=r.getLong(1);facturacion=dinero(r,2);ticket=dinero(r,3);
            }
        }
        long unidades,unidadesConCosto;BigDecimal ingresos,costos,ganancia;
        try(PreparedStatement p=c.prepareStatement(
            "SELECT COALESCE(SUM(D.cantidad),0),"+
            "COALESCE(SUM(CASE WHEN D.costo_unitario IS NOT NULL THEN D.cantidad ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN D.costo_unitario IS NOT NULL THEN D.cantidad*D.precio_unitario ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN D.costo_unitario IS NOT NULL THEN D.cantidad*D.costo_unitario ELSE 0 END),0) " +
            "FROM DetalleVenta D JOIN Venta V ON V.id=D.venta_id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<?")) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                r.next();unidades=r.getLong(1);unidadesConCosto=r.getLong(2);
                ingresos=dinero(r,3);costos=dinero(r,4);
            }
        }
        ganancia=ingresos.subtract(costos);
        return new Indicadores(operaciones,facturacion,ticket,unidades,unidadesConCosto,
            ingresos,costos,ganancia);
    }
    private static List<Dia> dias(Connection c,Timestamp inicio,Timestamp fin,
                                  LocalDate desde,LocalDate hasta)throws SQLException {
        Map<LocalDate,Dia> datos=new HashMap<>();
        try(PreparedStatement p=c.prepareStatement(
            "SELECT DATE(fecha_hora),COUNT(*),SUM(total) FROM Venta " +
            "WHERE fecha_hora>=? AND fecha_hora<? GROUP BY DATE(fecha_hora) ORDER BY DATE(fecha_hora)")) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    LocalDate d=r.getDate(1).toLocalDate();
                    datos.put(d,new Dia(d,r.getLong(2),dinero(r,3)));
                }
            }
        }
        List<Dia> resultado=new ArrayList<>();
        for(LocalDate d=desde;!d.isAfter(hasta);d=d.plusDays(1))
            resultado.add(datos.getOrDefault(d,new Dia(d,0,CERO)));
        return resultado;
    }
    private static List<Hora> horas(Connection c,Timestamp inicio,Timestamp fin)throws SQLException {
        Map<Integer,Hora> datos=new HashMap<>();
        try(PreparedStatement p=c.prepareStatement(
            "SELECT HOUR(fecha_hora),COUNT(*),SUM(total) FROM Venta " +
            "WHERE fecha_hora>=? AND fecha_hora<? GROUP BY HOUR(fecha_hora)")) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                while(r.next()){int h=r.getInt(1);datos.put(h,new Hora(h,r.getLong(2),dinero(r,3)));}
            }
        }
        List<Hora> resultado=new ArrayList<>();
        for(int h=0;h<24;h++)resultado.add(datos.getOrDefault(h,new Hora(h,0,CERO)));
        return resultado;
    }
    private static List<Medio> medios(Connection c,Timestamp inicio,Timestamp fin)throws SQLException {
        List<Medio> resultado=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement(
            "SELECT P.medio_pago,COUNT(*),COALESCE(SUM(P.monto),0) " +
            "FROM Pago P JOIN Venta V ON V.id=P.venta_id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<? GROUP BY P.medio_pago ORDER BY P.medio_pago")) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                while(r.next())resultado.add(new Medio(r.getString(1),r.getLong(2),dinero(r,3)));
            }
        }
        return resultado;
    }
    private static List<Vendedor> vendedores(Connection c,Timestamp inicio,Timestamp fin)throws SQLException {
        List<Vendedor> resultado=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement(
            "SELECT E.nombre,COUNT(V.id),COALESCE(SUM(V.total),0) " +
            "FROM Venta V JOIN Empleado E ON E.id=V.empleado_id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<? GROUP BY E.id,E.nombre " +
            "ORDER BY SUM(V.total) DESC,E.nombre")) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                while(r.next())resultado.add(new Vendedor(r.getString(1),r.getLong(2),dinero(r,3)));
            }
        }
        return resultado;
    }
    private static List<ProductoFila> productos(Connection c,Timestamp inicio,Timestamp fin,int dias)throws SQLException {
        List<ProductoFila> resultado=new ArrayList<>();
        // Se separa el agregado de detalles de Producto para incluir artículos sin ventas
        // y para que los cambios de costo actual no alteren márgenes históricos.
        String sql="SELECT P.codigo,P.nombre,P.estado,P.stock,P.stock_minimo,"+
            "COALESCE(S.unidades,0),COALESCE(S.facturacion,0),"+
            "COALESCE(S.unidades_con_costo,0),COALESCE(S.ingresos_con_costo,0),COALESCE(S.ganancia,0) " +
            "FROM Producto P LEFT JOIN (" +
            "SELECT D.producto_codigo,SUM(D.cantidad) AS unidades,"+
            "SUM(D.cantidad*D.precio_unitario) AS facturacion,"+
            "SUM(CASE WHEN D.costo_unitario IS NOT NULL THEN D.cantidad ELSE 0 END) AS unidades_con_costo,"+
            "SUM(CASE WHEN D.costo_unitario IS NOT NULL THEN D.cantidad*D.precio_unitario ELSE 0 END) AS ingresos_con_costo,"+
            "SUM(CASE WHEN D.costo_unitario IS NOT NULL THEN D.cantidad*(D.precio_unitario-D.costo_unitario) ELSE 0 END) AS ganancia " +
            "FROM DetalleVenta D JOIN Venta V ON V.id=D.venta_id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<? GROUP BY D.producto_codigo"+
            ") S ON S.producto_codigo=P.codigo ORDER BY S.unidades DESC,P.nombre";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            rango(p,inicio,fin);
            try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    String codigo=r.getString(1),nombre=r.getString(2);
                    boolean activo="ACTIVO".equals(r.getString(3));
                    int stock=r.getInt(4);Integer minimo=(Integer)r.getObject(5);
                    long unidades=r.getLong(6),conCosto=r.getLong(8);
                    BigDecimal facturacion=dinero(r,7),ingresoConCosto=dinero(r,9),ganancia=dinero(r,10);
                    BigDecimal cobertura=ReglasAnalisis.diasCobertura(stock,unidades,dias);
                    String sugerencia=ReglasAnalisis.oportunidad(activo,stock,minimo,unidades,dias,
                        conCosto,ingresoConCosto,ganancia);
                    resultado.add(new ProductoFila(codigo,nombre,activo,stock,minimo,unidades,
                        facturacion,conCosto,ingresoConCosto,ganancia,cobertura,sugerencia));
                }
            }
        }
        return resultado;
    }
}
