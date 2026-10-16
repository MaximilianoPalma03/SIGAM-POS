package ar.com.sigampos;

import ar.com.sigampos.servicio.CarritoOperaciones;
import ar.com.sigampos.modelo.LineaVenta;
import ar.com.sigampos.modelo.Producto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class PruebaCarrito {
    public static void main(String[] args) {
        Producto p=new Producto("QA05","Mouse",new BigDecimal("5000"),new BigDecimal("3000"),10,2,20,true);
        List<LineaVenta> carrito=new ArrayList<>();
        CarritoOperaciones.agregar(carrito,p,2);
        CarritoOperaciones.agregar(carrito,p,3);
        comprobar(carrito.size()==1&&carrito.get(0).cantidad()==5,"No consolida agregados");
        CarritoOperaciones.fijarCantidad(carrito,p,4);
        comprobar(carrito.size()==1&&carrito.get(0).cantidad()==4,"No permite editar cantidad");
        CarritoOperaciones.fijarCantidad(carrito,p,3);
        comprobar(carrito.get(0).cantidad()==3,"No resta unidad");
        boolean sinStock=false;
        try{CarritoOperaciones.fijarCantidad(carrito,p,11);}catch(IllegalArgumentException e){sinStock=true;}
        comprobar(sinStock&&carrito.get(0).cantidad()==3,"Stock excesivo alteró el carrito");
        CarritoOperaciones.fijarCantidad(carrito,p,0);
        comprobar(carrito.isEmpty(),"No elimina el producto con cantidad 0");
        System.out.println("PruebaCarrito: OK");
    }
    private static void comprobar(boolean condicion,String mensaje){if(!condicion)throw new AssertionError(mensaje);}
}
