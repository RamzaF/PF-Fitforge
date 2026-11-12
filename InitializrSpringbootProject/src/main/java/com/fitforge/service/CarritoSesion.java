package com.fitforge.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

public final class CarritoSesion {
    private CarritoSesion() {}
    public static HttpSession actual() {
        return ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest().getSession();
    }
    public static Object mutex() { return WebUtils.getSessionMutex(actual()); }
    public static long revision() { var r=actual().getAttribute("carritoRevision"); return r==null?0:(Long)r; }
    public static void modificado() { actual().setAttribute("carritoRevision",Math.incrementExact(revision())); }
}
