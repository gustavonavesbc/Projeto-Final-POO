'use strict';
let db={clientes:[],compras:[],vendas:[],lancamentos:[],resumo:{receitas:0,despesas:0,saldo:0}};
const $=id=>document.getElementById(id);
const dinheiro=n=>(Number(n)||0).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const dataBR=s=>s?s.split('-').reverse().join('/'):'—';
const txt=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const vazio=col=>`<tr><td colspan="${col}" class="empty">Nenhum registro ainda. Cadastre o primeiro acima!</td></tr>`;
const ordem=arr=>[...arr].sort((a,b)=>b.data.localeCompare(a.data)||b.id-a.id);
function aviso(m,erro=false){const el=$('notice');el.textContent=m;el.className='show'+(erro?' error':'');window.scrollTo({top:0,behavior:'smooth'});setTimeout(()=>el.classList.remove('show'),4200)}
function ir(aba){document.querySelectorAll('.section').forEach(s=>s.classList.toggle('active',s.id===aba));document.querySelectorAll('.nav').forEach(s=>s.classList.toggle('active',s.dataset.tab===aba));const titulos={inicio:'Visão geral',compras:'Compras',vendas:'Vendas',clientes:'Clientes',financeiro:'Financeiro'};const subtitulos={inicio:'Acompanhe a produção e as finanças da sua fazenda.',compras:'Controle os insumos e os fornecedores.',vendas:'Registre a comercialização da sua produção.',clientes:'Gerencie seus compradores.',financeiro:'Veja o resultado financeiro da fazenda.'};$('page-title').textContent=titulos[aba];$('breadcrumb').textContent=titulos[aba].toUpperCase();$('page-subtitle').textContent=subtitulos[aba];$('notice').className='';window.scrollTo({top:0})}
document.querySelectorAll('.nav').forEach(b=>b.addEventListener('click',()=>ir(b.dataset.tab)));
$('today').textContent=new Date().toLocaleDateString('pt-BR',{day:'2-digit',month:'long',year:'numeric'});
function excluir(tipo,id){if(!confirm('Deseja excluir este registro?'))return;enviar({acao:'excluir',tipo,id})}
async function enviar(obj){try{const r=await fetch('/api/acao',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body:new URLSearchParams(obj)});const j=await r.json();if(!r.ok)throw Error(j.erro||'Erro ao registrar.');await atualizar();aviso('Registro salvo com sucesso!')}catch(e){aviso(e.message,true)}}
for(const f of document.querySelectorAll('form[data-acao]')){f.addEventListener('submit',async e=>{e.preventDefault();const obj=Object.fromEntries(new FormData(f));obj.acao=f.dataset.acao;await enviar(obj);if(!$('notice').classList.contains('error')){f.reset();datasHoje()}})}
function datasHoje(){document.querySelectorAll('input[type=date]').forEach(input=>{if(!input.value)input.value=new Date().toLocaleDateString('en-CA')})}
function botao(tipo,id){return `<button class="delete" title="Excluir registro" onclick="excluir('${tipo}',${id})">Excluir</button>`}
function render(){const r=db.resumo;for(const suf of ['r','f']){for(const campo of ['receitas','despesas','saldo']){$(`${suf}-${campo}`).textContent=dinheiro(r[campo])}}$('r-clientes').textContent=db.clientes.length;
$('clientes-count').textContent=db.clientes.length+' clientes';$('compras-count').textContent=db.compras.length+' registros';$('vendas-count').textContent=db.vendas.length+' registros';
$('selecionar-cliente').innerHTML='<option value="">Selecione um cliente</option>'+[...db.clientes].sort((a,b)=>a.nome.localeCompare(b.nome)).map(c=>`<option value="${c.id}">${txt(c.nome)}</option>`).join('');
$('lista-clientes').innerHTML=db.clientes.map(c=>`<tr><td><b>${txt(c.nome)}</b></td><td>${txt(c.telefone||'—')}</td><td>${txt(c.cidade||'—')}</td><td>${db.vendas.filter(v=>v.clienteId===c.id).length}</td><td>${botao('cliente',c.id)}</td></tr>`).join('')||vazio(5);
$('lista-compras').innerHTML=ordem(db.compras).map(c=>`<tr><td>${dataBR(c.data)}</td><td>${txt(c.categoria)}</td><td><b>${txt(c.produto)}</b></td><td>${txt(c.fornecedor)}</td><td>${c.quantidade} ${txt(c.unidade)}</td><td class="negative">${dinheiro(c.valor)}</td><td>${botao('compra',c.id)}</td></tr>`).join('')||vazio(7);
$('lista-vendas').innerHTML=ordem(db.vendas).map(v=>`<tr><td>${dataBR(v.data)}</td><td><b>${txt(v.produto)}</b></td><td>${txt(v.cliente)}</td><td>${txt(v.vendedor)}</td><td>${v.quantidade} ${txt(v.unidade)}</td><td class="positive">${dinheiro(v.valor)}</td><td>${botao('venda',v.id)}</td></tr>`).join('')||vazio(7);
$('ultimas').innerHTML=ordem(db.vendas).slice(0,5).map(v=>`<tr><td>${dataBR(v.data)}</td><td><b>${txt(v.produto)}</b></td><td>${txt(v.cliente)}</td><td>${txt(v.vendedor)}</td><td class="positive">${dinheiro(v.valor)}</td></tr>`).join('')||vazio(5);
const linhas=[...db.compras.map(c=>({...c,origem:'Compra',descricao:c.produto+' • '+c.fornecedor,tipo:'despesa',entidade:'compra'})),...db.vendas.map(v=>({...v,origem:'Venda',descricao:v.produto+' • '+v.cliente,tipo:'receita',entidade:'venda'})),...db.lancamentos.map(l=>({...l,origem:'Avulso',entidade:'lancamento'}))];
$('lista-financeiro').innerHTML=ordem(linhas).map(l=>`<tr><td>${dataBR(l.data)}</td><td>${txt(l.origem)}</td><td>${txt(l.descricao)}</td><td><span class="tag ${l.tipo}">${l.tipo==='receita'?'Receita':'Despesa'}</span></td><td class="${l.tipo==='receita'?'positive':'negative'}">${l.tipo==='receita'?'+':'−'} ${dinheiro(l.valor)}</td><td>${l.entidade==='lancamento'?botao('lancamento',l.id):'—'}</td></tr>`).join('')||vazio(6);
}
async function atualizar(){const r=await fetch('/api/dados');if(!r.ok)throw Error('Servidor indisponível');db=await r.json();render()}
datasHoje();atualizar().catch(e=>aviso('Não foi possível carregar os dados. Inicie o sistema pelo Java: '+e.message,true));
