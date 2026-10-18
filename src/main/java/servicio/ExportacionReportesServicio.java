package ar.com.sigampos.servicio;

import ar.com.sigampos.datos.Conexion;
import ar.com.sigampos.modelo.Producto;
import ar.com.sigampos.util.ArchivoCsv;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Exportaciones administrativas; conservan los datos históricos sin mutar MySQL. */
public final class ExportacionReportesServicio {
    private final ProductoServicio productos = new ProductoServicio();
    private final JornadasServicio jornadas = new JornadasServicio();

    public int inventario(Path destino, String filtro) throws SQLException, IOException {
        List<List<?>> filas = new ArrayList<>();
        for (Producto p : productos.listar(filtro)) {
            filas.add(Arrays.asList(p.codigo(), p.nombre(), p.precioVenta(), p.costo(), p.stock(),
                p.stockMinimo(), p.stockMaximo(), p.activo() ? "ACTIVO" : "INACTIVO"));
        }
        ArchivoCsv.guardar(destino,List.of("Código","Nombre","Precio de venta ($)","Costo ($)",
            "Stock","Stock mínimo","Stock máximo","Estado"),filas);
        return filas.size();
    }

    /** Consulta agrupada: los pagos mixtos aparecen en columnas, una sola fila por venta. */
    public int historialVentas(Path destino, LocalDate desde, LocalDate hasta, String medio) throws SQLException, IOException {
        if(desde==null || hasta==null || desde.isAfter(hasta))
            throw new IllegalArgumentException("El período es inválido");
        String filtro = medio == null ? "TODOS" : medio;
        if (!List.of("TODOS","EFECTIVO","TRANSFERENCIA","TARJETA","QR").contains(filtro))
            throw new IllegalArgumentException("Medio de pago desconocido");
        String sql = "SELECT V.id,V.fecha_hora,V.caja_id,E.nombre,V.total," +
            "COALESCE(SUM(CASE WHEN P.medio_pago='EFECTIVO' THEN P.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN P.medio_pago='TRANSFERENCIA' THEN P.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN P.medio_pago='TARJETA' THEN P.monto ELSE 0 END),0)," +
            "COALESCE(SUM(CASE WHEN P.medio_pago='QR' THEN P.monto ELSE 0 END),0) " +
            "FROM Venta V JOIN Empleado E ON E.id=V.empleado_id LEFT JOIN Pago P ON P.venta_id=V.id " +
            "WHERE V.fecha_hora>=? AND V.fecha_hora<? " +
            "AND (?='TODOS' OR EXISTS(SELECT 1 FROM Pago PF WHERE PF.venta_id=V.id AND PF.medio_pago=?)) " +
            "GROUP BY V.id,V.fecha_hora,V.caja_id,E.nombre,V.total ORDER BY V.fecha_hora DESC,V.id DESC";
        List<List<?>> filas = new ArrayList<>();
        try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setTimestamp(1,Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2,Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            ps.setString(3,filtro);ps.setString(4,filtro);
            try(ResultSet r=ps.executeQuery()){
                while(r.next())filas.add(Arrays.asList(r.getLong(1),r.getTimestamp(2),r.getLong(3),
                    r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getBigDecimal(7),
                    r.getBigDecimal(8),r.getBigDecimal(9)));
            }
        }
        ArchivoCsv.guardar(destino,List.of("Venta","Fecha y hora","Caja","Empleado","Total ($)",
            "Efectivo ($)","Transferencia ($)","Tarjeta ($)","QR ($)"),filas);
        return filas.size();
    }

    private record Arqueos(BigDecimal esperadoEfectivo,BigDecimal contadoEfectivo,
        BigDecimal fondoProximoEfectivo,BigDecimal inicialDigital,BigDecimal entradasDigital,
        BigDecimal salidasDigital,BigDecimal esperadoDigital,BigDecimal declaradoDigital,
        BigDecimal diferenciaDigital,BigDecimal fondoProximoDigital) {}

    /** Consulta los cierres físicos y digitales en bloque, sin multiplicar jornadas por movimientos. */
    public int historialJornadas(Path destino,LocalDate desde,LocalDate hasta,String estado) throws SQLException,IOException {
        List<JornadasServicio.Jornada> lista=jornadas.listar(desde,hasta,null,
            "TODOS".equals(estado)?null:estado);
        Map<Long,Arqueos> arqueos=new HashMap<>();
        if(!lista.isEmpty()){
            String marcas=String.join(",",Collections.nCopies(lista.size(),"?"));
            String sql="SELECT C.id,A.efectivo_esperado,A.efectivo_contado,A.fondo_proximo_turno,"+
                "D.saldo_inicial,"+
                "COALESCE((SELECT SUM(M.monto) FROM MovimientoCajaDigital M WHERE M.caja_digital_id=D.id AND M.tipo='ENTRADA'),0),"+
                "COALESCE((SELECT SUM(M.monto) FROM MovimientoCajaDigital M WHERE M.caja_digital_id=D.id AND M.tipo='SALIDA'),0),"+
                "AD.saldo_esperado,AD.saldo_declarado,AD.diferencia,AD.fondo_proximo_turno " +
                "FROM Caja C LEFT JOIN ArqueoCaja A ON A.caja_id=C.id " +
                "LEFT JOIN CajaDigital D ON D.caja_id=C.id " +
                "LEFT JOIN ArqueoCajaDigital AD ON AD.caja_digital_id=D.id " +
                "WHERE C.id IN ("+marcas+")";
            try(Connection c=Conexion.abrir();PreparedStatement ps=c.prepareStatement(sql)){
                for(int i=0;i<lista.size();i++)ps.setLong(i+1,lista.get(i).cajaId());
                try(ResultSet r=ps.executeQuery()){
                    while(r.next())arqueos.put(r.getLong(1),new Arqueos(
                        r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),
                        r.getBigDecimal(5),r.getBigDecimal(6),r.getBigDecimal(7),
                        r.getBigDecimal(8),r.getBigDecimal(9),r.getBigDecimal(10),r.getBigDecimal(11)));
                }
            }
        }
        List<List<?>> filas=new ArrayList<>();
        for(JornadasServicio.Jornada j:lista){
            Arqueos a=arqueos.get(j.cajaId());
            BigDecimal esperadoFisico=j.fondo().add(j.efectivo()).add(j.entradas()).subtract(j.salidas());
            BigDecimal inicialDigital=a==null?null:a.inicialDigital();
            BigDecimal esperadoDigital=inicialDigital==null?null:inicialDigital.add(j.digital())
                .add(a.entradasDigital()).subtract(a.salidasDigital());
            filas.add(Arrays.asList(j.cajaId(),j.empleado(),j.turno(),j.inicio(),j.fin(),j.estado(),
                j.fondo(),j.efectivo(),j.entradas(),j.salidas(),
                a!=null&&a.esperadoEfectivo()!=null?a.esperadoEfectivo():esperadoFisico,
                a==null?null:a.contadoEfectivo(),j.diferencia(),a==null?null:a.fondoProximoEfectivo(),
                inicialDigital,inicialDigital==null?null:j.digital(),
                inicialDigital==null?null:a.entradasDigital(),inicialDigital==null?null:a.salidasDigital(),
                a!=null&&a.esperadoDigital()!=null?a.esperadoDigital():esperadoDigital,
                a==null?null:a.declaradoDigital(),a==null?null:a.diferenciaDigital(),
                a==null?null:a.fondoProximoDigital()));
        }
        ArchivoCsv.guardar(destino,List.of("Caja","Empleado","Turno","Apertura","Cierre","Estado",
            "Fondo inicial efectivo ($)","Cobros efectivo ($)","Entradas efectivo ($)","Salidas efectivo ($)",
            "Efectivo esperado ($)","Efectivo contado ($)","Diferencia efectivo ($)","Fondo próximo efectivo ($)",
            "Saldo inicial MP ($)","Cobros MP ($)","Entradas MP ($)","Salidas MP ($)",
            "Saldo esperado MP ($)","Saldo declarado MP ($)","Diferencia MP ($)","Fondo próximo MP ($)"),filas);
        return filas.size();
    }

    /** Movimientos físicos y digitales de una sola jornada, con medio digital explícito. */
    public int movimientosCaja(Path destino,long cajaId) throws SQLException,IOException {
        List<List<?>> filas = new ArrayList<>();
        try(Connection c = Conexion.abrir()) {
            try(PreparedStatement ps = c.prepareStatement("SELECT M.fecha_hora,M.tipo,M.monto,M.motivo,E.nombre " +
                "FROM MovimientoCaja M JOIN Empleado E ON E.id=M.empleado_id WHERE M.caja_id=? ORDER BY M.id")) {
                ps.setLong(1,cajaId);
                try(ResultSet rs=ps.executeQuery()) {
                    while(rs.next())filas.add(Arrays.asList(rs.getTimestamp(1),"EFECTIVO",rs.getString(2),"",
                        rs.getBigDecimal(3),rs.getString(4),rs.getString(5)));
                }
            }
            try(PreparedStatement ps=c.prepareStatement("SELECT M.fecha_hora,M.tipo,M.medio_digital,M.monto,M.motivo,E.nombre " +
                "FROM MovimientoCajaDigital M JOIN CajaDigital D ON D.id=M.caja_digital_id " +
                "JOIN Empleado E ON E.id=M.empleado_id WHERE D.caja_id=? ORDER BY M.id")) {
                ps.setLong(1,cajaId);
                try(ResultSet rs=ps.executeQuery()) {
                    while(rs.next())filas.add(Arrays.asList(rs.getTimestamp(1),"MERCADO PAGO",rs.getString(2),
                        rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getString(6)));
                }
            }
        }
        filas.sort(Comparator.comparing(f -> (Timestamp)f.get(0)));
        ArchivoCsv.guardar(destino,List.of("Fecha","Caja de destino","Operación","Medio digital",
            "Importe ($)","Motivo","Empleado"),filas);
        return filas.size();
    }
}
