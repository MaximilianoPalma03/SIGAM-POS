package ar.com.sigampos.servicio;

import ar.com.sigampos.modelo.LineaVenta;
import ar.com.sigampos.modelo.Producto;
import java.util.List;
import java.util.Objects;

/** Reglas de cantidad del carrito, sin dependencia de Swing ni de la base de datos. */
public final class CarritoOperaciones {
    private CarritoOperaciones() {}

    public static void agregar(List<LineaVenta> carrito,Producto producto,int adicional) {
        Objects.requireNonNull(carrito,"Carrito obligatorio");
        Objects.requireNonNull(producto,"Producto obligatorio");
        if(adicional<=0)throw new IllegalArgumentException("La cantidad a agregar debe ser mayor que cero");
        int indice=indice(carrito,producto.codigo());
        int anterior=indice>=0?carrito.get(indice).cantidad():0;
        fijarCantidad(carrito,producto,Math.addExact(anterior,adicional));
    }

    public static void fijarCantidad(List<LineaVenta> carrito,Producto producto,int cantidad) {
        Objects.requireNonNull(carrito,"Carrito obligatorio");
        Objects.requireNonNull(producto,"Producto obligatorio");
        if(cantidad<0)throw new IllegalArgumentException("La cantidad no puede ser negativa");
        int indice=indice(carrito,producto.codigo());
        if(cantidad==0) {
            if(indice>=0)carrito.remove(indice);
            return;
        }
        if(!producto.activo())throw new IllegalArgumentException("El producto está inactivo");
        if(cantidad>producto.stock())
            throw new IllegalArgumentException("Stock insuficiente. Disponibles: "+producto.stock());
        LineaVenta nueva=new LineaVenta(producto,cantidad);
        if(indice>=0)carrito.set(indice,nueva);
        else carrito.add(nueva);
    }

    private static int indice(List<LineaVenta> carrito,String codigo) {
        for(int i=0;i<carrito.size();i++)
            if(carrito.get(i).producto().codigo().equals(codigo))return i;
        return -1;
    }
}
