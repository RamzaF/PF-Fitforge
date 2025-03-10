package com.fitforge.service;

import com.fitforge.domain.Contacto;
import java.util.List;
/**
 *
 * @author bryam
 */
public interface ContactoService{

    public List<Contacto> getContacto(boolean activos);
    public Contacto getContacto(Contacto contacto);
    public void save(Contacto contacto);
    public void delete(Contacto contacto);
}


