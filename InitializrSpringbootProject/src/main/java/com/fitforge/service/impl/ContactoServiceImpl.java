package com.fitforge.service.impl;

import com.fitforge.dao.ContactoDao;
import com.fitforge.domain.Contacto;
import com.fitforge.service.ContactoService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author bryam
 */
@Service
public class ContactoServiceImpl implements ContactoService{
@Autowired
    private ContactoDao contactoDao;

    @Override
    @Transactional(readOnly = true)
    public List<Contacto> getContacto(boolean activos) {
        var lista = contactoDao.findAll();
        if (activos) {
            lista.removeIf(e -> !e.isActivo());
        }
        return lista;
    }

    @Override
    @Transactional(readOnly = true)
    public Contacto getContacto(Contacto contacto) {
        return contactoDao.findById(contacto.getIdContacto()).orElse(null);
    }

    @Override
    @Transactional
    public void save(Contacto contacto) {
        contactoDao.save(contacto);
    }

    @Override
    @Transactional
    public void delete(Contacto contacto) {
        contactoDao.delete(contacto);
    }
}