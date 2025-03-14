package com.fitforge.service;

import com.fitforge.domain.Contacto;
import java.util.List;

/**
 *
 * @author bryam
 */
public interface ContactoService {

    public List<Contacto> getContactos(boolean activos);
    public Contacto getContacto(Contacto contacto);
    public void saveContacto(Contacto contacto);
    public void deleteContacto(Contacto contacto);
}


