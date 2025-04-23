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

import com.fitforge.domain.Factura;
import com.fitforge.domain.Item;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.domain.Venta;
import com.fitforge.service.FacturaService;
import com.fitforge.service.ItemService;
import com.fitforge.service.ProductoColorTallaService;
import com.fitforge.service.VentaService;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ProductoColorTallaService productoColorTallaService;

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private VentaService ventaService;

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
        var items = itemService.getItems();
        System.out.println("🛒 Carrito contiene: " + items.size() + " ítems");
        items.forEach(i -> System.out.println(i)); // Imprime usando el toString() 
        model.addAttribute("items", items); // Agrega la lista de items al modelo
        model.addAttribute("total", itemService.getTotal());
        return "carrito/listado";
    }

    @GetMapping("/agregar/{id}")
    public String agregar(@PathVariable("id") Long id) {
        if (!esUsuarioNormal()) {
            return "redirect:/";
        }
        ProductoColorTalla pct = productoColorTallaService.getProductoColorTalla(id);

        if (pct == null || pct.getIdProductoColorTalla() == null) {
            // System.out.println("❌ ProductoColorTalla no encontrado o ID nulo: " + id);
            return "redirect:/carrito/ver";
        }

        if (pct.getExistencias() != null && pct.getExistencias() > 0) {
            itemService.save(new Item(pct));
            // System.out.println("✅ Producto agregado al carrito: " + pct.getIdProductoColorTalla());
        } else {
            // System.out.println("⚠️ Producto sin existencias: " + id);
            // System.out.println("⚠️ Producto sin existencias: " + pct.getExistencias());
        }

        return "redirect:/carrito/ver";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") Long id) {
        if (!esUsuarioNormal()) {
            return "redirect:/";
        }
        itemService.delete(id);
        return "redirect:/carrito/ver";
    }

    @PostMapping("/actualizar")
    public String actualizar(@RequestParam("id") Long id,
                             @RequestParam("cantidad") int cantidad) {
        if (!esUsuarioNormal()) {
            return "redirect:/";
        }
        itemService.updateCantidad(id, cantidad);
        return "redirect:/carrito/ver";
    }

    @PostMapping("/confirmar-pedido")
    public String confirmarPedido(RedirectAttributes redirectAttributes, Authentication authentication) {
        try {
            // Obtener usuario autenticado
            String username = authentication.getName();
            // Obtener items del carrito
            var items = itemService.getItems();
            if (items == null || items.isEmpty()) {
                redirectAttributes.addFlashAttribute("mensaje", "El carrito está vacío.");
                redirectAttributes.addFlashAttribute("tipo", "warning");
                return "redirect:/carrito/ver";
            }
            // Calcular total
            double total = itemService.getTotal();
            // Crear factura
            Factura factura = new Factura();
            factura.setUsuario(username);
            factura.setFecha(new java.sql.Date(System.currentTimeMillis()));
            factura.setTotal(total);
            factura.setEstado("Pendiente");
            facturaService.guardar(factura);
            // Crear ventas (detalle)
            for (Item item : items) {
                Venta venta = new Venta();
                venta.setFactura(factura);
                venta.setProductoColorTalla(item.getProductoColorTalla());
                venta.setPrecio(item.getProductoColorTalla().getProducto().getPrecio());
                venta.setCantidad(item.getCantidad());
                ventaService.guardar(venta);
            }
            // Limpiar carrito
            itemService.clear();
            redirectAttributes.addFlashAttribute("mensaje", "¡Pedido realizado con éxito!");
            redirectAttributes.addFlashAttribute("tipo", "success");
            return "redirect:/carrito/ver";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error al procesar el pedido: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
            return "redirect:/carrito/ver";
        }
    }
}
