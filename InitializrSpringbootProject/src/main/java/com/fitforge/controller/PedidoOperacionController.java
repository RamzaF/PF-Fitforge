package com.fitforge.controller;

import com.fitforge.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PedidoOperacionController {
    private final PedidoOperacionService operaciones;
    private final PedidoService pedidos;
    public PedidoOperacionController(PedidoOperacionService operaciones,PedidoService pedidos) {this.operaciones=operaciones;this.pedidos=pedidos;}
    private String listado(boolean admin,String estado,String q,int pagina,Model model) {
        model.addAttribute("resultado",operaciones.listar(admin,estado,q,pagina));model.addAttribute("administrativo",admin);
        model.addAttribute("estado",estado);model.addAttribute("q",q);model.addAttribute("estados",PedidoOperacionService.ESTADOS);
        return "pedidos/listado";
    }
    @GetMapping("/admin/pedidos")
    public String admin(@RequestParam(defaultValue="") String estado,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int pagina,Model model) {
        return listado(true,estado,q,pagina,model);
    }
    @GetMapping("/pedidos")
    public String propios(@RequestParam(defaultValue="") String estado,@RequestParam(defaultValue="0") int pagina,Model model) {
        return listado(false,estado,"",pagina,model);
    }
    @GetMapping("/admin/pedidos/{id}")
    public String detalle(@PathVariable long id,Model model) {
        model.addAttribute("detalle",operaciones.detalleAdmin(id));return "admin/pedidos/detalle";
    }
    @GetMapping("/pedidos/{id}")
    public String propio(@PathVariable long id) {pedidos.obtenerPropio(id);return "redirect:/carrito/factura/"+id;}

    @PostMapping("/admin/pedidos/{id}/estado")
    public Object cambiar(@PathVariable long id,@RequestParam String origen,@RequestParam String destino,
            org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        boolean changed=operaciones.transicionar(id,origen,destino);
        flash.addFlashAttribute("mensaje",changed?"Estado registrado. Despacho manual, sin envío físico.":"La transición ya estaba registrada.");
        var redirect=new org.springframework.web.servlet.view.RedirectView("/admin/pedidos/"+id);
        redirect.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);return redirect;
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.web.servlet.ModelAndView conflicto(org.springframework.web.server.ResponseStatusException e) {
        if(e.getStatusCode().value()!=409) throw e;
        return new org.springframework.web.servlet.ModelAndView("pedidos/error",java.util.Map.of("mensaje",e.getReason()),org.springframework.http.HttpStatus.CONFLICT);
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class,jakarta.persistence.PersistenceException.class})
    public org.springframework.web.servlet.ModelAndView indisponible(Exception e, jakarta.servlet.http.HttpServletRequest request) {
        boolean lectura="GET".equals(request.getMethod());
        String volver=request.getRequestURI().startsWith("/admin/")?"/admin/pedidos":"/pedidos";
        String mensaje=lectura?"No se pudieron cargar los pedidos. Recargá la página e intentá nuevamente.":
            "No se pudo verificar el cambio. Recargá el detalle y reintentá la misma transición; no se duplicará la auditoría.";
        return new org.springframework.web.servlet.ModelAndView("pedidos/error",java.util.Map.of(
            "mensaje",mensaje,"volver",volver,"titulo",lectura?"Pedidos no disponibles":"No se confirmó un nuevo cambio"),org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }
}
