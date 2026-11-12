// Run in the pinned Node container; no dependencies or browser DOM emulation package.
const {readFileSync} = require('node:fs');
const {runInNewContext} = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync(process.argv[2], 'utf8');
function load(nodes = {}, selected = {}) {
    const window = { location: { href: '' } };
    runInNewContext(source, {
        document: {
            addEventListener: (event, fn) => { if (event === 'DOMContentLoaded') fn(); },
            getElementById: id => nodes[id] || null,
            querySelectorAll: selector => selected[selector] || []
        }, window, console
    });
    return window;
}
// Home/category have no filter DOM: initialization must not throw.
load();
let focused = false;
const events = {};
const modal = { addEventListener: (event, fn) => { events[event] = fn; } };
load({ filtroModal: modal });
events['hidden.bs.modal'](); // Missing opener must also be safe.
load({ filtroModal: modal, botonAbrirModal: { focus: () => { focused = true; } } });
events['hidden.bs.modal']();
assert.equal(focused, true);
const clicks = {};
const window = load({
    filtroModal: modal, aplicarFiltros: { addEventListener: (event, fn) => { clicks[event] = fn; } },
    precioMin: {value: '10'}, precioMax: {value: '30'}
}, {'input[name="categorias"]:checked': [{value:'1'}]});
clicks.click();
assert.equal(window.location.href, '/tienda/filtrar?categorias=1&precioMin=10&precioMax=30');
console.log('PASS: absent DOM, modal focus, absent opener, filter navigation');
