// Abre/fecha o menu lateral em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Destaca no menu a seção atual
const secao = '/' + location.pathname.split('/')[1];
document.querySelectorAll('#sidebar .nav-link').forEach(link => {
    if (link.getAttribute('href') === secao) {
        link.classList.add('ativo');
    }
});
