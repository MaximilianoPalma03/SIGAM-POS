package ar.com.sigampos.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Preferencia local del comercio: el cálculo sugerido de precio está deshabilitado por defecto.
 * No modifica la estructura MySQL, ni cambia precios existentes al activar la opción.
 */
public final class ConfiguracionProductos {
    private static final String CLAVE_RECARGO="productos.precioPorRecargo";
    private final Preferences preferencias;

    public ConfiguracionProductos(){
        this(Preferences.userNodeForPackage(ConfiguracionProductos.class));
    }

    // Inyección para pruebas, evitando alterar la configuración real del usuario.
    public ConfiguracionProductos(Preferences preferencias){
        this.preferencias=Objects.requireNonNull(preferencias);
    }

    public boolean precioPorRecargoActivo(){return preferencias.getBoolean(CLAVE_RECARGO,false);}

    public void establecerPrecioPorRecargo(boolean habilitado){
        preferencias.putBoolean(CLAVE_RECARGO,habilitado);
        try{preferencias.flush();}
        catch(BackingStoreException e){
            throw new IllegalStateException("No se pudo guardar la configuración local de productos",e);
        }
    }

    /** Recargo sobre costo: $10.000 + 30% = $13.000; NO es margen sobre precio final. */
    public static BigDecimal precioSugerido(BigDecimal costo,BigDecimal recargo){
        if(costo==null || costo.signum()<=0)
            throw new IllegalArgumentException("El costo debe ser mayor que cero");
        if(recargo==null || recargo.signum()<0 || recargo.compareTo(new BigDecimal("10000"))>0)
            throw new IllegalArgumentException("El recargo debe estar entre 0% y 10000%");
        return costo.multiply(BigDecimal.ONE.add(recargo.movePointLeft(2)))
            .setScale(2,RoundingMode.HALF_UP);
    }

    /** Porcentaje orientativo para cargar un producto ya registrado. */
    public static BigDecimal recargoExistente(BigDecimal costo,BigDecimal precio){
        if(costo==null||costo.signum()<=0||precio==null)return BigDecimal.ZERO;
        BigDecimal recargo=precio.subtract(costo).multiply(new BigDecimal("100"))
            .divide(costo,2,RoundingMode.HALF_UP);
        return recargo.max(BigDecimal.ZERO).min(new BigDecimal("10000"));
    }
}
