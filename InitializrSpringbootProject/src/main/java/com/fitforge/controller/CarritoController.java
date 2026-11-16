package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fitforge.domain.Item;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.ItemService;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    @Autowired
    private ItemService itemService;

    @Autowired private com.fitforge.service.PedidoService pedidos;
    @Autowired private com.fitforge.service.ConfirmacionCarritoService confirmaciones;

    private boolean esUsuarioNormal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority().equals("ROLE_ADMIN")) {
                return false;
            }
        }
        return true;
    }

    @GetMapping("")
    public String redirigirCarrito() {
        return "redirect:/carrito/ver";
    }

    @GetMapping("/ver")
    public String verCarrito(Model model) {
        if (!esUsuarioNormal()) {
            return "redirect:/";
        }
        synchronized(com.fitforge.service.CarritoSesion.mutex()) {
            var items = itemService.getItems();
            model.addAttribute("items", items);
            model.addAttribute("total", items.stream().mapToDouble(Item::getSubtotal).sum());
            model.addAttribute("claveConfirmacion", confirmaciones.preparar(items));
            model.addAttribute("requiereConsulta", confirmaciones.requiereConsulta());
            model.addAttribute("entrega",confirmaciones.entregaActual());
        }
        return "carrito/listado";
    }

    @PostMapping("/agregar/{id}")
    public String agregar(@PathVariable("id") Long id,
                          @RequestParam(defaultValue = "1") int cantidad,
                          RedirectAttributes redirectAttributes) {
        var variant = new ProductoColorTalla();
        variant.setIdProductoColorTalla(id);
        var item = new Item(variant);
        item.setCantidad(cantidad);
        try {
            itemService.save(item);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/carrito/ver";
    }

    @PostMapping("/agregar")
    public String agregarSeleccion(@RequestParam("id") Long id,
                                    @RequestParam(defaultValue = "1") int cantidad,
                                    RedirectAttributes redirectAttributes) {
        return agregar(id, cantidad, redirectAttributes);
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") Long id) {
        itemService.delete(id);
        return "redirect:/carrito/ver";
    }

    @PostMapping("/actualizar")
    public String actualizar(@RequestParam("id") Long id,
                             @RequestParam("cantidad") int cantidad,
                             RedirectAttributes redirectAttributes) {
        try {
            itemService.updateCantidad(id, cantidad);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/carrito/ver";
    }

    @PostMapping("/confirmar-pedido")
    public Object confirmarPedido(@RequestParam("clave") String clave,
            @RequestParam(defaultValue="") String destinatario,@RequestParam(defaultValue="") String telefonoEntrega,
            @RequestParam(defaultValue="") String direccionEntrega,@RequestParam(defaultValue="") String notaEntrega,
            @RequestParam(defaultValue="false") boolean consentimiento,
            RedirectAttributes redirectAttributes) {
        String target;
        try { target="/carrito/factura/"+confirmaciones.confirmar(clave,new com.fitforge.service.EntregaPedido(destinatario,telefonoEntrega,direccionEntrega,notaEntrega,consentimiento)).idFactura(); }
        catch(com.fitforge.service.PedidoException e) {
            redirectAttributes.addFlashAttribute("error",e.getMessage()); target="/carrito/ver";
        } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | jakarta.persistence.PersistenceException e) {
            return new org.springframework.web.servlet.ModelAndView("carrito/error", java.util.Map.of(),
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        var redirect=new org.springframework.web.servlet.view.RedirectView(target);
        redirect.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER); return redirect;
    }

    @GetMapping("/factura/{id}")
    public String verFactura(@PathVariable("id") Long id, Model model) {
        var receipt=pedidos.obtenerPropio(id);
        model.addAttribute("factura",receipt);
        model.addAttribute("ventas",receipt.lineas());
        return "carrito/factura";
    }
}
