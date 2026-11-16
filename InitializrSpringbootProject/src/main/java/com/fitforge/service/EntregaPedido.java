package com.fitforge.service;

/** Explicitly fictitious delivery intent, independent of the mutable customer profile. */
public record EntregaPedido(String destinatario,String telefono,String direccion,String nota,boolean consentimiento) {
    public static EntregaPedido demo() {
        return new EntregaPedido("Persona ficticia","000-000-0000","Calle Demo 123, Ciudad Ficticia","Solo demostración",true);
    }
    private static String texto(String s) {return s==null?"":s.strip().replaceAll("\\s+"," ");}
    public EntregaPedido validar() {
        var d=new EntregaPedido(texto(destinatario),texto(telefono),texto(direccion),texto(nota),consentimiento);
        if(!d.consentimiento || d.destinatario.length()<3 || d.destinatario.length()>80 || d.telefono.length()<7 || d.telefono.length()>20
            || !d.telefono.matches("[0-9+() -]+") || d.direccion.length()<10 || d.direccion.length()>240 || d.nota.length()>240
            || java.util.stream.Stream.of(d.destinatario,d.telefono,d.direccion,d.nota).anyMatch(v->v.chars().anyMatch(Character::isISOControl)))
            throw new PedidoException("ENTREGA_INVALIDA","Completá un destino ficticio válido y aceptá el uso exclusivo de datos de demostración.");
        return d;
    }
    public String huella() {
        var s=new StringBuilder();
        for(String value:java.util.List.of(destinatario,telefono,direccion,nota)) s.append('|').append(value.length()).append(':').append(value);
        return s.toString();
    }
}
