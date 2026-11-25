import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.swing.*;
import java.awt.Desktop;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Projeto de POO: Fazenda Amigão. Java 17+, sem dependências externas. */
public class FazendaAmigao {
    static final Path BASE = Paths.get(System.getProperty("user.dir"));
    static final Path ARQUIVO = BASE.resolve("dados/fazenda.dat");
    static Banco banco;

    // HERANÇA: Cliente e Vendedor são especializações de Pessoa.
    static abstract class Pessoa implements Serializable {
        private static final long serialVersionUID = 1L;
        String nome;
        Pessoa(String nome) { this.nome = nome; }
    }
    static class Cliente extends Pessoa {
        private static final long serialVersionUID = 1L;
        int id; String telefone; String cidade;
        Cliente(int id, String nome, String telefone, String cidade) { super(nome); this.id=id; this.telefone=telefone; this.cidade=cidade; }
    }
    static class Vendedor extends Pessoa {
        private static final long serialVersionUID = 1L;
        Vendedor(String nome) { super(nome); }
    }
    static class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        String descricao, unidade; double quantidade, precoUnitario;
        Item(String descricao, String unidade, double quantidade, double precoUnitario) {
            this.descricao=descricao; this.unidade=unidade; this.quantidade=quantidade; this.precoUnitario=precoUnitario;
        }
        double total() { return quantidade * precoUnitario; }
    }
    // CLASSE ABSTRATA + POLIMORFISMO: cada transação define o efeito no caixa.
    static abstract class Transacao implements Serializable {
        private static final long serialVersionUID = 1L;
        int id; String data; Item item;
        Transacao(int id, String data, Item item) { this.id=id; this.data=data; this.item=item; }
        abstract double impactoFinanceiro();
        double valor() { return item.total(); }
    }
    static class Compra extends Transacao {
        private static final long serialVersionUID = 1L;
        String fornecedor, categoria;
        Compra(int id, String data, Item item, String fornecedor, String categoria) {
            super(id,data,item); this.fornecedor=fornecedor; this.categoria=categoria;
        }
        @Override double impactoFinanceiro() { return -valor(); }
    }
    static class Venda extends Transacao {
        private static final long serialVersionUID = 1L;
        int clienteId; Vendedor vendedor;
        Venda(int id, String data, Item item, int clienteId, Vendedor vendedor) {
            super(id,data,item); this.clienteId=clienteId; this.vendedor=vendedor;
        }
        @Override double impactoFinanceiro() { return valor(); }
    }
    static class Lancamento implements Serializable {
        private static final long serialVersionUID = 1L;
        int id; String data, descricao, tipo; double valor;
        Lancamento(int id, String data, String descricao, String tipo, double valor) {
            this.id=id; this.data=data; this.descricao=descricao; this.tipo=tipo; this.valor=valor;
        }
        double impactoFinanceiro() { return "receita".equals(tipo) ? valor : -valor; }
    }
    // AGREGAÇÃO: o banco reúne registros, preservados por serialização em arquivo.
    static class Banco implements Serializable {
        private static final long serialVersionUID = 1L;
        int proximoId=1;
        ArrayList<Cliente> clientes=new ArrayList<>();
        ArrayList<Compra> compras=new ArrayList<>();
        ArrayList<Venda> vendas=new ArrayList<>();
        ArrayList<Lancamento> lancamentos=new ArrayList<>();
        int novoId() { return proximoId++; }
        Cliente cliente(int id) { return clientes.stream().filter(c -> c.id==id).findFirst().orElse(null); }
        double saldo() {
            double total=0;
            for(Transacao t: compras) total+=t.impactoFinanceiro();
            for(Transacao t: vendas) total+=t.impactoFinanceiro();
            for(Lancamento l: lancamentos) total+=l.impactoFinanceiro();
            return total;
        }
        double receitas() { double total=0; for(Venda v:vendas) total+=v.valor(); for(Lancamento l:lancamentos) if("receita".equals(l.tipo)) total+=l.valor; return total; }
        double despesas() { double total=0; for(Compra c:compras) total+=c.valor(); for(Lancamento l:lancamentos) if("despesa".equals(l.tipo)) total+=l.valor; return total; }
    }
    static class DadosInvalidosException extends Exception { DadosInvalidosException(String m) {super(m);} }
    static String exigir(Map<String,String> p,String k) throws DadosInvalidosException {
        String v=p.getOrDefault(k,"").trim(); if(v.isEmpty()) throw new DadosInvalidosException("Preencha o campo: "+k); return v;
    }
    static int inteiro(Map<String,String> p,String k) throws DadosInvalidosException {
        try {return Integer.parseInt(exigir(p,k));} catch(NumberFormatException ex) {throw new DadosInvalidosException("Número inválido: "+k);}
    }
    static double positivo(Map<String,String> p,String k) throws DadosInvalidosException {
        try {double n=new BigDecimal(exigir(p,k).replace(",",".")).doubleValue(); if(!Double.isFinite(n)||n<=0) throw new Exception(); return n;}
        catch(Exception ex){throw new DadosInvalidosException("Informe um valor maior que zero: "+k);}
    }
    static synchronized void salvar() throws IOException {
        Files.createDirectories(ARQUIVO.getParent());
        Path temp=ARQUIVO.resolveSibling("fazenda.tmp");
        try(ObjectOutputStream o=new ObjectOutputStream(Files.newOutputStream(temp))) {o.writeObject(banco);}
        try {Files.move(temp,ARQUIVO,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException ex){Files.move(temp,ARQUIVO,StandardCopyOption.REPLACE_EXISTING);}
    }
    static Banco carregar() throws Exception {
        if(!Files.exists(ARQUIVO)) return new Banco();
        try(ObjectInputStream in=new ObjectInputStream(Files.newInputStream(ARQUIVO))) {return (Banco) in.readObject();}
    }
    static String esc(String s) {return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","").replace("\t"," ");}
    static String str(String s) {return "\""+esc(s)+"\"";}
    static String num(double x) {return String.format(Locale.ROOT,"%.2f",x);}
    static String item(Item i) {return "\"produto\":"+str(i.descricao)+",\"unidade\":"+str(i.unidade)+",\"quantidade\":"+num(i.quantidade)+",\"preco\":"+num(i.precoUnitario)+",\"valor\":"+num(i.total());}
    static String json() {
        StringBuilder s=new StringBuilder("{\"clientes\":[");
        for(Cliente c:banco.clientes) {if(s.charAt(s.length()-1)!='[') s.append(','); s.append("{\"id\":").append(c.id).append(",\"nome\":").append(str(c.nome)).append(",\"telefone\":").append(str(c.telefone)).append(",\"cidade\":").append(str(c.cidade)).append('}');}
        s.append("],\"compras\":[");
        for(Compra c:banco.compras){if(s.charAt(s.length()-1)!='[')s.append(',');s.append("{\"id\":").append(c.id).append(",\"data\":").append(str(c.data)).append(",\"fornecedor\":").append(str(c.fornecedor)).append(",\"categoria\":").append(str(c.categoria)).append(',').append(item(c.item)).append('}');}
        s.append("],\"vendas\":[");
        for(Venda v:banco.vendas){if(s.charAt(s.length()-1)!='[')s.append(',');s.append("{\"id\":").append(v.id).append(",\"data\":").append(str(v.data)).append(",\"clienteId\":").append(v.clienteId).append(",\"cliente\":").append(str(banco.cliente(v.clienteId)==null?"Cliente removido":banco.cliente(v.clienteId).nome)).append(",\"vendedor\":").append(str(v.vendedor.nome)).append(',').append(item(v.item)).append('}');}
        s.append("],\"lancamentos\":[");
        for(Lancamento l:banco.lancamentos){if(s.charAt(s.length()-1)!='[')s.append(',');s.append("{\"id\":").append(l.id).append(",\"data\":").append(str(l.data)).append(",\"descricao\":").append(str(l.descricao)).append(",\"tipo\":").append(str(l.tipo)).append(",\"valor\":").append(num(l.valor)).append('}');}
        return s.append("],\"resumo\":{\"receitas\":").append(num(banco.receitas())).append(",\"despesas\":").append(num(banco.despesas())).append(",\"saldo\":").append(num(banco.saldo())).append("}}").toString();
    }
    static Map<String,String> params(String form) {
        Map<String,String> p=new HashMap<>();
        for(String parte: form.split("&")){String[] a=parte.split("=",2);try {p.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8), URLDecoder.decode(a.length>1?a[1]:"",StandardCharsets.UTF_8));}catch(Exception ignored){}}
        return p;
    }
    static synchronized String executar(Map<String,String> p) throws Exception {
        String acao=exigir(p,"acao");
        if("cliente".equals(acao)) {
            String nome=exigir(p,"nome");
            banco.clientes.add(new Cliente(banco.novoId(),nome,p.getOrDefault("telefone",""),p.getOrDefault("cidade","")));
        } else if("compra".equals(acao)) {
            String data=exigir(p,"data"), produto=exigir(p,"produto"), fornecedor=exigir(p,"fornecedor");
            String categoria=exigir(p,"categoria"), unidade=exigir(p,"unidade");
            double quantidade=positivo(p,"quantidade"), preco=positivo(p,"preco");
            banco.compras.add(new Compra(banco.novoId(),data,new Item(produto,unidade,quantidade,preco),fornecedor,categoria));
        } else if("venda".equals(acao)) {
            String data=exigir(p,"data"), produto=exigir(p,"produto"), vendedor=exigir(p,"vendedor"), unidade=exigir(p,"unidade");
            int clienteId=inteiro(p,"clienteId");
            if(banco.cliente(clienteId)==null)throw new DadosInvalidosException("Cadastre ou selecione um cliente válido.");
            double quantidade=positivo(p,"quantidade"),preco=positivo(p,"preco");
            banco.vendas.add(new Venda(banco.novoId(),data,new Item(produto,unidade,quantidade,preco),clienteId,new Vendedor(vendedor)));
        } else if("lancamento".equals(acao)) {
            String data=exigir(p,"data"), descricao=exigir(p,"descricao"), tipo=exigir(p,"tipo");
            if(!tipo.equals("receita")&&!tipo.equals("despesa")) throw new DadosInvalidosException("Tipo inválido.");
            banco.lancamentos.add(new Lancamento(banco.novoId(),data,descricao,tipo,positivo(p,"valor")));
        } else if("excluir".equals(acao)) {
            String tipo=exigir(p,"tipo");int id=inteiro(p,"id");boolean removido;
            switch(tipo) {
                case "cliente":
                    if(banco.vendas.stream().anyMatch(v->v.clienteId==id))throw new DadosInvalidosException("Cliente com vendas registradas não pode ser excluído.");
                    removido=banco.clientes.removeIf(c->c.id==id);break;
                case "compra":removido=banco.compras.removeIf(c->c.id==id);break;
                case "venda":removido=banco.vendas.removeIf(v->v.id==id);break;
                case "lancamento":removido=banco.lancamentos.removeIf(l->l.id==id);break;
                default:throw new DadosInvalidosException("Tipo inválido.");
            }
            if(!removido) throw new DadosInvalidosException("Registro não encontrado.");
        } else throw new DadosInvalidosException("Ação desconhecida.");
        salvar();return "{\"ok\":true}";
    }
    static void resposta(HttpExchange e,int status,String tipo,byte[] b) throws IOException {
        e.getResponseHeaders().set("Content-Type",tipo);e.getResponseHeaders().set("Cache-Control","no-store");
        e.sendResponseHeaders(status,b.length);try(OutputStream o=e.getResponseBody()){o.write(b);}
    }
    static void texto(HttpExchange e,int status,String s) throws IOException {resposta(e,status,"application/json; charset=utf-8",s.getBytes(StandardCharsets.UTF_8));}
    static void abrirSite() {try {Desktop.getDesktop().browse(URI.create("http://127.0.0.1:8787"));}catch(Exception ex){JOptionPane.showMessageDialog(null,"Abra http://127.0.0.1:8787 no navegador.");}}
    public static void main(String[] args) throws Exception {
        try {banco=carregar();} catch(Exception ex){System.err.println("Erro ao abrir dados. Faça uma cópia de segurança de dados/fazenda.dat. "+ex.getMessage());return;}
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",8787),0);
        server.createContext("/api/dados",e->{try{if(!e.getRequestMethod().equals("GET")){texto(e,405,"{\"erro\":\"Método não permitido\"}");return;} synchronized(FazendaAmigao.class){texto(e,200,json());}}catch(Exception ex){texto(e,500,"{\"erro\":"+str(ex.getMessage())+"}");}});
        server.createContext("/api/acao",e->{try {if(!e.getRequestMethod().equals("POST")){texto(e,405,"{\"erro\":\"Método não permitido\"}");return;}String body=new String(e.getRequestBody().readNBytes(100_000),StandardCharsets.UTF_8);texto(e,200,executar(params(body)));}catch(DadosInvalidosException ex){texto(e,400,"{\"erro\":"+str(ex.getMessage())+"}");}catch(Exception ex){ex.printStackTrace();texto(e,500,"{\"erro\":\"Erro ao salvar os dados.\"}");}});
        server.createContext("/",e->{String path=e.getRequestURI().getPath();if(path.equals("/"))path="/index.html";if(!List.of("/index.html","/style.css","/app.js").contains(path)){texto(e,404,"{\"erro\":\"Não encontrado\"}");return;}Path f=BASE.resolve("web"+path);if(!Files.exists(f)){texto(e,404,"{\"erro\":\"Arquivo não encontrado\"}");return;}String tipo=path.endsWith(".css")?"text/css":path.endsWith(".js")?"application/javascript":"text/html";resposta(e,200,tipo+"; charset=utf-8",Files.readAllBytes(f));});
        server.setExecutor(Executors.newCachedThreadPool());server.start();
        System.out.println("Fazenda Amigão: http://127.0.0.1:8787");
        if(!java.awt.GraphicsEnvironment.isHeadless()) SwingUtilities.invokeLater(()->{
            JFrame janela=new JFrame("Fazenda Amigão | Sistema de Gestão");janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JPanel painel=new JPanel();painel.setLayout(new BoxLayout(painel,BoxLayout.Y_AXIS));painel.setBorder(BorderFactory.createEmptyBorder(28,32,28,32));
            JLabel titulo=new JLabel("FAZENDA AMIGÃO");titulo.setFont(titulo.getFont().deriveFont(23f));titulo.setAlignmentX(.5f);
            JLabel desc=new JLabel("Sistema Java orientado a objetos");desc.setAlignmentX(.5f);
            JButton botao=new JButton("Abrir sistema no navegador");botao.setAlignmentX(.5f);botao.addActionListener(ev->abrirSite());
            painel.add(titulo);painel.add(Box.createVerticalStrut(15));painel.add(desc);painel.add(Box.createVerticalStrut(25));painel.add(botao);
            janela.add(painel);janela.pack();janela.setLocationRelativeTo(null);janela.setVisible(true);
            abrirSite();
        });
    }
}
