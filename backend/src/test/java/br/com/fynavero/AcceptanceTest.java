package br.com.fynavero;

import br.com.fynavero.shared.Mailer;
import br.com.fynavero.ledger.Transaction.TransactionRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import br.com.fynavero.notification.NotificationService;
import br.com.fynavero.notification.NotificationMailer;
import br.com.fynavero.overview.MonthSummaryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AcceptanceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NotificationMailer notificationMailer;
    @Autowired NotificationService actionService;
    @Autowired TransactionRepository transactionRepository;
    @Autowired MonthSummaryService monthSummaryService;
    @MockitoSpyBean Mailer mailer;

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private final String month = YearMonth.from(today).toString();

    private JsonNode call(MockHttpSession s, MockHttpServletRequestBuilder req, Object body, int expected) throws Exception {
        req.session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(body instanceof String str ? str : json.writeValueAsString(body));
        var res = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
        String text = res.getContentAsString();
        return text.isEmpty() ? null : json.readTree(text);
    }

    /** Cada cadastro de teste vem de um "endereço" diferente, para não esbarrar no limite de contas por IP. */
    private static RequestPostProcessor fromIp(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String randomIp() {
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        return "10." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + rnd.nextInt(1, 255);
    }

    /** Último link enviado por e-mail que contém o caminho informado (ex.: "/redefinir-senha"). */
    private String lastLinkToken(String to, String path) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mailer, atLeastOnce()).send(eq(to), anyString(), text.capture(), any());
        for (int i = text.getAllValues().size() - 1; i >= 0; i--) {
            Matcher m = Pattern.compile(Pattern.quote(path) + "\\?token=([\\w-]+)").matcher(text.getAllValues().get(i));
            if (m.find()) return m.group(1);
        }
        throw new AssertionError("nenhum link " + path + " enviado para " + to);
    }

    /** Cria conta, conclui o onboarding e devolve a sessão. */
    private MockHttpSession newUser(String contributionJson) throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = "p" + UUID.randomUUID().toString().substring(0, 8) + "@teste.dev";
        call(s, post("/api/auth/register").with(fromIp(randomIp())),
                "{\"name\":\"Pessoa\",\"email\":\"" + email + "\",\"password\":\"senhaforte1\",\"acceptTerms\":true}", 200);
        JsonNode catalog = call(s, get("/api/catalog"), null, 200);
        long mercado = 0, dizimo = 0;
        for (JsonNode c : catalog) {
            if (c.get("name").asText().equals("Mercado")) mercado = c.get("id").asLong();
            if (c.get("name").asText().equals("Dízimo")) dizimo = c.get("id").asLong();
        }
        call(s, post("/api/onboarding"), "{\"name\":\"Pessoa\",\"objectives\":[\"ORGANIZAR\",\"INVESTIR\"],\"incomes\":[],"
                + "\"catalogIds\":[" + mercado + "," + dizimo + "],"
                + "\"customCategories\":[{\"name\":\"Feira\",\"icon\":\"carrot\",\"group\":\"Essenciais\"}],"
                + "\"fixedBills\":[],\"contribution\":" + contributionJson + "}", 200);
        return s;
    }

    private long category(MockHttpSession s, String name) throws Exception {
        for (JsonNode c : call(s, get("/api/categories"), null, 200)) {
            if (c.get("name").asText().equals(name)) return c.get("id").asLong();
        }
        throw new AssertionError("categoria " + name);
    }

    private JsonNode launch(MockHttpSession s, String kind, long cents, long categoryId, String extra) throws Exception {
        return call(s, post("/api/transactions"), "{\"kind\":\"" + kind + "\",\"amountCents\":" + cents
                + ",\"categoryId\":" + categoryId + ",\"date\":\"" + today + "\"" + extra + "}", 200);
    }

    @Test
    void semLoginNaoAcessaNada() throws Exception {
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void duasContasNuncaEnxergamOsDadosUmaDaOutra() throws Exception {
        MockHttpSession a = newUser("null");
        MockHttpSession b = newUser("null");
        long txA = launch(a, "DESPESA", 12_345, category(a, "Mercado"), "").get(0).get("id").asLong();
        long catA = category(a, "Mercado");

        assertThat(call(b, get("/api/transactions?month=" + month), null, 200)).isEmpty();
        call(b, put("/api/transactions/" + txA), "{\"amountCents\":1,\"categoryId\":" + catA + ",\"date\":\"" + today + "\"}", 404);
        call(b, delete("/api/transactions/" + txA), null, 404);
        call(b, post("/api/transactions/" + txA + "/duplicate"), null, 404);
        // B não pode lançar usando a categoria de A
        call(b, post("/api/transactions"), "{\"kind\":\"DESPESA\",\"amountCents\":100,\"categoryId\":" + catA + "}", 400);
        call(b, put("/api/categories/" + catA), "{\"name\":\"invadido\"}", 404);
        assertThat(call(b, get("/api/dashboard?month=" + month), null, 200).get("current").get("despesas").asLong()).isZero();

        assertThat(call(a, get("/api/transactions?month=" + month), null, 200)).hasSize(1);
    }

    @Test
    void compraDe4900Em10VezesGera10ParcelasDe490() throws Exception {
        MockHttpSession s = newUser("null");
        JsonNode parcels = launch(s, "DESPESA", 490_000, category(s, "Mercado"), ",\"installments\":10,\"description\":\"TV\"");
        assertThat(parcels).hasSize(10);
        for (JsonNode p : parcels) assertThat(p.get("amountCents").asLong()).isEqualTo(49_000);
        assertThat(parcels.get(9).get("installmentNumber").asInt()).isEqualTo(10);
        assertThat(parcels.get(9).get("status").asText()).isEqualTo("PREVISTO");
        assertThat(parcels.get(1).get("status").asText()).isEqualTo("PREVISTO");

        JsonNode inst = call(s, get("/api/installments?month=" + month), null, 200);
        assertThat(inst.get("plans").get(0).get("count").asInt()).isEqualTo(10);
        assertThat(inst.get("plans").get(0).get("remainingCents").asLong()).isEqualTo(9 * 49_000);
    }

    @Test
    void contribuicaoDe10PorCentoEInvestimentoNoInicio() throws Exception {
        MockHttpSession s = newUser("{\"enabled\":true,\"bps\":1000}");
        assertThat(call(s, get("/api/dashboard?month=" + month), null, 200).get("empty").asBoolean()).isTrue();
        launch(s, "RECEITA", 357_500, category(s, "Outras receitas"), "");
        JsonNode dash = call(s, get("/api/dashboard?month=" + month), null, 200);
        assertThat(dash.get("empty").asBoolean()).isFalse();
        JsonNode cur = dash.get("current");
        assertThat(cur.get("contribuicao").asLong()).isEqualTo(35_750);
        assertThat(cur.get("despesas").asLong()).isEqualTo(35_750);
        long saldoAntes = cur.get("saldo").asLong();

        launch(s, "INVESTIMENTO", 20_000, category(s, "Reserva de emergência"), "");
        JsonNode depois = call(s, get("/api/dashboard?month=" + month), null, 200).get("current");
        assertThat(depois.get("despesas").asLong()).isEqualTo(35_750);
        assertThat(saldoAntes - depois.get("saldo").asLong()).isEqualTo(20_000);
    }

    @Test
    void excluirEDesfazer() throws Exception {
        MockHttpSession s = newUser("null");
        long id = launch(s, "DESPESA", 5_000, category(s, "Feira"), "").get(0).get("id").asLong();
        JsonNode del = call(s, delete("/api/transactions/" + id), null, 200);
        assertThat(call(s, get("/api/transactions?month=" + month), null, 200)).isEmpty();
        call(s, post("/api/transactions/restore"), "{\"ids\":" + del.get("ids") + "}", 204);
        assertThat(call(s, get("/api/transactions?month=" + month), null, 200)).hasSize(1);
    }

    @Test
    void excluirCompraParceladaInteiraEDesfazer() throws Exception {
        MockHttpSession s = newUser("null");
        long cat = category(s, "Mercado");
        JsonNode parcels = launch(s, "DESPESA", 155_970, cat, ",\"installments\":9,\"paymentMethod\":\"CREDITO\"");
        long plan = parcels.get(0).get("installmentPlanId").asLong();
        long third = parcels.get(2).get("id").asLong();

        // "Todas as parcelas" a partir de qualquer parcela apaga a compra inteira, inclusive as anteriores.
        JsonNode all = call(s, delete("/api/transactions/" + third + "?scope=ALL"), null, 200);
        assertThat(all.get("ids")).hasSize(9);
        assertThat(call(s, get("/api/installments?month=" + month), null, 200).get("plans")).isEmpty();
        call(s, post("/api/transactions/restore"), "{\"ids\":" + all.get("ids") + "}", 204);
        assertThat(call(s, get("/api/installments?month=" + month), null, 200).get("plans")).hasSize(1);

        // Botão "Excluir" no cartão de Parcelas e Dívidas.
        JsonNode viaPlan = call(s, delete("/api/installment-plans/" + plan), null, 200);
        assertThat(viaPlan.get("ids")).hasSize(9);
        assertThat(call(s, get("/api/installments?month=" + month), null, 200).get("plans")).isEmpty();
    }

    @Test
    void marcarComoPagoPorEnganoPodeSerDesfeito() throws Exception {
        MockHttpSession s = newUser("null");
        JsonNode bill = call(s, post("/api/transactions"), "{\"kind\":\"DESPESA\",\"amountCents\":5000,\"categoryId\":"
                + category(s, "Mercado") + ",\"date\":\"" + today.plusDays(3) + "\",\"description\":\"Internet\"}", 200).get(0);
        long id = bill.get("id").asLong();
        assertThat(call(s, post("/api/transactions/" + id + "/mark-paid"), null, 200).get("status").asText()).isEqualTo("REALIZADO");
        assertThat(call(s, post("/api/transactions/" + id + "/mark-unpaid"), null, 200).get("status").asText()).isEqualTo("PREVISTO");
    }

    @Test
    void importarEDesfazerRemoveSoAqueleLote() throws Exception {
        MockHttpSession s = newUser("null");
        long mercado = category(s, "Mercado");
        launch(s, "DESPESA", 1_000, mercado, "");
        String row = "{\"kind\":\"DESPESA\",\"date\":\"" + today.withDayOfMonth(1) + "\",\"categoryId\":" + mercado + ",\"amountCents\":%d}";
        JsonNode batch = call(s, post("/api/imports"), "{\"fileName\":\"planilha.xlsx\",\"rows\":["
                + row.formatted(116_042) + "," + row.formatted(2_000) + "]}", 200);
        assertThat(batch.get("despesas").asLong()).isEqualTo(118_042);
        assertThat(call(s, get("/api/transactions?month=" + month), null, 200)).hasSize(3);

        JsonNode dup = call(s, post("/api/imports/check"),
                "{\"rows\":[{\"date\":\"" + today.withDayOfMonth(1) + "\",\"amountCents\":116042}]}", 200);
        assertThat(dup.get("duplicateIndexes")).hasSize(1);

        call(s, delete("/api/imports/" + batch.get("id").asLong()), null, 200);
        JsonNode rest = call(s, get("/api/transactions?month=" + month), null, 200);
        assertThat(rest).hasSize(1);
        assertThat(rest.get(0).get("amountCents").asLong()).isEqualTo(1_000);
    }

    @Test
    void relatorioAnualEExportacao() throws Exception {
        MockHttpSession s = newUser("null");
        launch(s, "DESPESA", 10_000, category(s, "Mercado"), "");
        JsonNode r = call(s, get("/api/reports/annual?year=" + today.getYear()), null, 200);
        assertThat(r.get("totalDespesas").get("total").asLong()).isEqualTo(10_000);
        assertThat(r.get("totalDespesas").get("average").asLong()).isEqualTo(10_000);
        for (String f : new String[]{"xlsx", "pdf"}) {
            var res = mvc.perform(get("/api/reports/annual/export?year=" + today.getYear() + "&format=" + f).session(s))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertThat(res.getContentAsByteArray().length).isGreaterThan(500);
        }
    }

    @Test
    void variosObjetivosERendaVariavelComDia() throws Exception {
        MockHttpSession s = newUser("null");
        JsonNode me = call(s, get("/api/auth/me"), null, 200);
        assertThat(me.get("objectives")).hasSize(2);
        call(s, post("/api/income-sources"), "{\"name\":\"Transágua\",\"amountCents\":130000,\"fixed\":false,\"day\":20}", 200);
        JsonNode recs = call(s, get("/api/recurrences"), null, 200);
        assertThat(recs.toString()).contains("Transágua (valor estimado)");
        JsonNode inc = call(s, get("/api/income-sources"), null, 200);
        assertThat(inc.get(0).get("day").asInt()).isEqualTo(20);
    }

    @Test
    void investimentoEntraMesmoComSaldoNegativo() throws Exception {
        MockHttpSession s = newUser("null");
        launch(s, "INVESTIMENTO", 999_999, category(s, "Reserva de emergência"), "");
        JsonNode cur = call(s, get("/api/dashboard?month=" + month), null, 200).get("current");
        assertThat(cur.get("investido").asLong()).isEqualTo(999_999);
        assertThat(cur.get("saldo").asLong()).isEqualTo(-999_999);
    }

    @Test
    void emprestimoRecebidoComParcelasDePagamento() throws Exception {
        MockHttpSession s = newUser("{\"enabled\":true,\"bps\":1000}");
        JsonNode created = launch(s, "RECEITA", 500_000, category(s, "Empréstimo"),
                ",\"loan\":{\"count\":12,\"installmentCents\":55000}");
        assertThat(created).hasSize(13);
        JsonNode cur = call(s, get("/api/dashboard?month=" + month), null, 200).get("current");
        assertThat(cur.get("emprestimos").asLong()).isEqualTo(500_000);
        assertThat(cur.get("receitas").asLong()).isEqualTo(500_000);
        assertThat(cur.get("contribuicao").asLong()).isZero();
        JsonNode plan = call(s, get("/api/installments?month=" + month), null, 200).get("plans").get(0);
        assertThat(plan.get("count").asInt()).isEqualTo(12);
        assertThat(plan.get("installmentCents").asLong()).isEqualTo(55_000);
        assertThat(plan.get("totalCents").asLong()).isEqualTo(660_000);
        // Só receita de empréstimo aceita parcelas de pagamento
        call(s, post("/api/transactions"), "{\"kind\":\"RECEITA\",\"amountCents\":100,\"categoryId\":"
                + category(s, "Outras receitas") + ",\"loan\":{\"count\":2,\"installmentCents\":50}}", 400);
    }

    @Test
    void creditoComValorDeCadaParcela() throws Exception {
        MockHttpSession s = newUser("null");
        JsonNode parcels = launch(s, "DESPESA", 49_000, category(s, "Mercado"),
                ",\"installments\":10,\"installmentValue\":true,\"paymentMethod\":\"CREDITO\"");
        assertThat(parcels).hasSize(10);
        for (JsonNode p : parcels) assertThat(p.get("amountCents").asLong()).isEqualTo(49_000);
    }

    @Test
    void parcelarLancamentoExistenteEMudarNumeroDeParcelas() throws Exception {
        MockHttpSession s = newUser("null");
        long cat = category(s, "Mercado");
        long id = launch(s, "DESPESA", 120_000, cat, "").get(0).get("id").asLong();
        JsonNode parcels = call(s, post("/api/transactions/" + id + "/installments"), "{\"amountCents\":120000,\"categoryId\":"
                + cat + ",\"date\":\"" + today + "\",\"count\":3,\"installmentValue\":false,\"paymentMethod\":\"CREDITO\"}", 200);
        assertThat(parcels).hasSize(3);
        assertThat(parcels.get(0).get("amountCents").asLong()).isEqualTo(40_000);
        long plan = parcels.get(0).get("installmentPlanId").asLong();

        JsonNode more = call(s, put("/api/installment-plans/" + plan), "{\"count\":5}", 200);
        assertThat(more).hasSize(5);
        for (JsonNode p : more) assertThat(p.get("installmentTotal").asInt()).isEqualTo(5);
        assertThat(more.get(4).get("amountCents").asLong()).isEqualTo(40_000);

        JsonNode fewer = call(s, put("/api/installment-plans/" + plan), "{\"count\":2}", 200);
        assertThat(fewer).hasSize(2);
        assertThat(call(s, get("/api/installments?month=" + month), null, 200).get("plans").get(0).get("count").asInt()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ etiquetas, limites, objetivos e avisos

    private long userId(MockHttpSession s) throws Exception {
        return call(s, get("/api/auth/me"), null, 200).get("id").asLong();
    }

    @Test
    void coresEscolhidasParaCategoriaEtiquetaEGraficos() throws Exception {
        MockHttpSession s = newUser("null");
        long mercado = category(s, "Mercado");
        launch(s, "DESPESA", 1_000, mercado, ",\"tags\":[\"viagem\"]");

        JsonNode cat = call(s, put("/api/categories/" + mercado), "{\"name\":\"Mercado\",\"color\":\"chart-9\"}", 200);
        assertThat(cat.get("color").asText()).isEqualTo("chart-9");
        call(s, put("/api/categories/" + mercado), "{\"name\":\"Mercado\",\"color\":\"#ff0000\"}", 400);

        JsonNode tag = call(s, put("/api/transactions/tags/color"), "{\"tag\":\"#Viagem\",\"color\":\"chart-7\"}", 200);
        assertThat(tag.get("tag").asText()).isEqualTo("viagem");
        assertThat(tag.get("count").asInt()).isEqualTo(1);
        assertThat(call(s, get("/api/transactions/tags"), null, 200).get(0).get("color").asText()).isEqualTo("chart-7");
        call(s, put("/api/transactions/tags/color"), "{\"tag\":\"viagem\",\"color\":null}", 200);
        assertThat(call(s, get("/api/transactions/tags"), null, 200).get(0).get("color").isNull()).isTrue();

        JsonNode me = call(s, put("/api/profile/chart-colors"), "{\"income\":\"chart-8\",\"expense\":null,\"invest\":\"chart-12\"}", 200);
        assertThat(me.get("chartColors").get("income").asText()).isEqualTo("chart-8");
        assertThat(me.get("chartColors").get("expense").isNull()).isTrue();
        assertThat(call(s, get("/api/auth/me"), null, 200).get("chartColors").get("invest").asText()).isEqualTo("chart-12");
        call(s, put("/api/profile/chart-colors"), "{\"income\":\"chart-13\"}", 400);
    }

    @Test
    void etiquetasEBuscaNoAnoTodo() throws Exception {
        MockHttpSession s = newUser("null");
        long mercado = category(s, "Mercado");
        JsonNode tx = launch(s, "DESPESA", 4_590, mercado,
                ",\"description\":\"Farmácia do centro #Saúde\",\"tags\":[\"Viagem\",\"#viagem\"]").get(0);
        assertThat(tx.get("tags")).extracting(JsonNode::asText).containsExactly("viagem", "saúde");
        assertThat(tx.get("description").asText()).isEqualTo("Farmácia do centro");
        launch(s, "DESPESA", 30_000, mercado, ",\"description\":\"Supermercado\"");

        JsonNode found = call(s, get("/api/transactions/search?q=farmacia"), null, 200);
        assertThat(found.get("items")).hasSize(1);
        assertThat(found.get("despesasCents").asLong()).isEqualTo(4_590);
        assertThat(call(s, get("/api/transactions/search?tag=viagem"), null, 200).get("items")).hasSize(1);
        assertThat(call(s, get("/api/transactions").param("month", month).param("q", "#viagem"), null, 200)).hasSize(1);
        assertThat(call(s, get("/api/transactions/tags"), null, 200).get(0).get("tag").asText()).isIn("viagem", "saúde");

        long id = tx.get("id").asLong();
        JsonNode edited = call(s, put("/api/transactions/" + id), "{\"amountCents\":4590,\"categoryId\":" + mercado
                + ",\"date\":\"" + today + "\",\"description\":\"Farmácia\",\"tags\":[\"reforma\"]}", 200);
        assertThat(edited.get("tags")).extracting(JsonNode::asText).containsExactly("reforma");
    }

    @Test
    void limiteDeCategoriaAvisaEm80E100PorCento() throws Exception {
        MockHttpSession s = newUser("null");
        long mercado = category(s, "Mercado");
        call(s, put("/api/categories/" + mercado), "{\"name\":\"Mercado\",\"goalCents\":100000}", 200);

        launch(s, "DESPESA", 85_000, mercado, "");
        JsonNode budget = call(s, get("/api/budgets?month=" + month), null, 200).get(0);
        assertThat(budget.get("pctBps").asInt()).isEqualTo(8_500);
        assertThat(budget.get("level").asText()).isEqualTo("ALERTA");
        JsonNode alert = call(s, get("/api/notifications"), null, 200).get(0);
        assertThat(alert.get("type").asText()).isEqualTo("LIMITE_ALERTA");
        assertThat(alert.get("title").asText()).isEqualTo("Mercado: 85% do limite");

        long uid = userId(s);
        assertThat(notificationMailer.sendLimitAlerts(uid)).isEqualTo(1);
        assertThat(notificationMailer.sendLimitAlerts(uid)).isZero(); // o mesmo aviso não sai duas vezes

        launch(s, "DESPESA", 20_000, mercado, "");
        assertThat(call(s, get("/api/budgets?month=" + month), null, 200).get(0).get("level").asText()).isEqualTo("ESTOURADO");
        assertThat(notificationMailer.sendLimitAlerts(uid)).isEqualTo(1);
    }

    @Test
    void objetivoDeEconomiaComPrazoEProgresso() throws Exception {
        MockHttpSession s = newUser("null");
        LocalDate deadline = today.plusMonths(4);
        JsonNode goal = call(s, post("/api/goals"), "{\"name\":\"Viagem\",\"targetCents\":120000,\"deadline\":\""
                + deadline + "\",\"initialCents\":20000}", 200);
        assertThat(goal.get("savedCents").asLong()).isEqualTo(20_000);
        assertThat(goal.get("pctBps").asInt()).isEqualTo(1_666);
        assertThat(goal.get("monthsLeft").asInt()).isEqualTo(5);
        assertThat(goal.get("monthlyNeededCents").asLong()).isEqualTo(24_000);
        assertThat(goal.get("status").asText()).isEqualTo("ATENCAO");

        launch(s, "INVESTIMENTO", 4_000, goal.get("id").asLong(), "");
        JsonNode after = call(s, get("/api/goals"), null, 200).get(0);
        assertThat(after.get("savedCents").asLong()).isEqualTo(24_000);
        assertThat(after.get("status").asText()).isEqualTo("NO_RITMO");

        call(s, post("/api/goals"), "{\"name\":\"viagem\",\"targetCents\":5000}", 400);
        call(s, delete("/api/goals/" + goal.get("id").asLong()), null, 204);
        assertThat(call(s, get("/api/goals"), null, 200)).isEmpty();
    }

    @Test
    void contaDeAmanhaGeraAvisoEMarcarComoPagaPeloEmail() throws Exception {
        MockHttpSession s = newUser("null");
        LocalDate tomorrow = today.plusDays(1);
        JsonNode bill = call(s, post("/api/transactions"), "{\"kind\":\"DESPESA\",\"amountCents\":90000,\"categoryId\":"
                + category(s, "Mercado") + ",\"date\":\"" + tomorrow + "\",\"description\":\"Aluguel\"}", 200).get(0);
        long billId = bill.get("id").asLong();
        assertThat(bill.get("status").asText()).isEqualTo("PREVISTO");

        JsonNode n = call(s, get("/api/notifications"), null, 200).get(0);
        assertThat(n.get("type").asText()).isEqualTo("CONTA_AMANHA");
        assertThat(n.get("title").asText()).isEqualTo("Aluguel vence amanhã");
        assertThat(n.get("transactionId").asLong()).isEqualTo(billId);

        long uid = userId(s);
        assertThat(notificationMailer.sendBillReminders(uid)).isEqualTo(1);
        assertThat(notificationMailer.sendBillReminders(uid)).isZero();

        String link = actionService.payLink(transactionRepository.findById(billId).orElseThrow());
        String token = link.substring(link.indexOf("token=") + 6);
        MockHttpSession anonymous = new MockHttpSession();
        assertThat(call(anonymous, post("/api/actions/describe"), "{\"token\":\"" + token + "\"}", 200).get("paid").asBoolean()).isFalse();
        JsonNode paid = call(anonymous, post("/api/actions/pay"), "{\"token\":\"" + token + "\"}", 200);
        assertThat(paid.get("paid").asBoolean()).isTrue();
        assertThat(paid.get("description").asText()).isEqualTo("Aluguel");
        call(anonymous, post("/api/actions/describe"), "{\"token\":\"token-invalido\"}", 400);

        JsonNode list = call(s, get("/api/transactions?month=" + YearMonth.from(tomorrow)), null, 200);
        for (JsonNode t : list) if (t.get("id").asLong() == billId) assertThat(t.get("status").asText()).isEqualTo("REALIZADO");
        assertThat(call(s, get("/api/notifications"), null, 200)).isEmpty();
    }

    @Test
    void resumoDoMesEPreferenciasDeAviso() throws Exception {
        MockHttpSession s = newUser("null");
        launch(s, "RECEITA", 357_500, category(s, "Salário"), "");
        launch(s, "DESPESA", 50_000, category(s, "Mercado"), "");
        launch(s, "DESPESA", 12_000, category(s, "Feira"), "");

        var summary = monthSummaryService.summary(userId(s), YearMonth.from(today));
        assertThat(summary.receitas()).isEqualTo(357_500);
        assertThat(summary.despesas()).isEqualTo(62_000);
        assertThat(summary.topCategory()).isEqualTo("Mercado");
        assertThat(summary.saldo()).isEqualTo(295_500);

        assertThat(call(s, post("/api/notifications/test-summary"), null, 200).get("month").asText())
                .isEqualTo(YearMonth.from(today).minusMonths(1).toString());
        call(s, put("/api/notifications/preferences"), "{\"bills\":true,\"limits\":false,\"summary\":false}", 200);
        JsonNode prefs = call(s, get("/api/notifications/preferences"), null, 200);
        assertThat(prefs.get("limits").asBoolean()).isFalse();
        assertThat(prefs.get("bills").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ lançamento: senha, e-mail e limites

    @Test
    void esqueciMinhaSenhaRedefineEEntra() throws Exception {
        MockHttpSession s = newUser("null");
        String email = call(s, get("/api/auth/me"), null, 200).get("email").asText();
        MockHttpSession anon = new MockHttpSession();
        call(anon, post("/api/auth/password/forgot").with(fromIp(randomIp())), "{\"email\":\"ninguem@teste.dev\"}", 204);
        call(anon, post("/api/auth/password/forgot").with(fromIp(randomIp())), "{\"email\":\"" + email + "\"}", 204);
        String token = lastLinkToken(email, "/redefinir-senha");

        call(anon, post("/api/auth/password/reset"), "{\"token\":\"" + token + "\",\"password\":\"curta\"}", 400);
        JsonNode me = call(anon, post("/api/auth/password/reset"), "{\"token\":\"" + token + "\",\"password\":\"novasenha99\"}", 200);
        assertThat(me.get("email").asText()).isEqualTo(email);
        call(new MockHttpSession(), post("/api/auth/password/reset"), "{\"token\":\"" + token + "\",\"password\":\"outrasenha1\"}", 400);

        call(new MockHttpSession(), post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"senhaforte1\"}", 401);
        call(new MockHttpSession(), post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"novasenha99\"}", 200);
    }

    @Test
    void cadastroPedeConfirmacaoDeEmail() throws Exception {
        MockHttpSession s = newUser("null");
        JsonNode me = call(s, get("/api/auth/me"), null, 200);
        assertThat(me.get("emailVerified").asBoolean()).isFalse();
        String email = me.get("email").asText();

        call(s, post("/api/auth/email/resend"), null, 204);
        String token = lastLinkToken(email, "/confirmar-email");
        JsonNode verified = call(new MockHttpSession(), post("/api/auth/email/verify"), "{\"token\":\"" + token + "\"}", 200);
        assertThat(verified.get("emailVerified").asBoolean()).isTrue();
        assertThat(call(s, get("/api/auth/me"), null, 200).get("emailVerified").asBoolean()).isTrue();
        call(new MockHttpSession(), post("/api/auth/email/verify"), "{\"token\":\"" + token + "\"}", 400);
    }

    @Test
    void limiteDeCadastrosPorRede() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            String body = "{\"name\":\"Robô\",\"email\":\"r" + UUID.randomUUID().toString().substring(0, 8)
                    + "@teste.dev\",\"password\":\"senhaforte1\",\"acceptTerms\":true}";
            call(new MockHttpSession(), post("/api/auth/register").with(fromIp(ip)), body, 200);
        }
        JsonNode blocked = call(new MockHttpSession(), post("/api/auth/register").with(fromIp(ip)),
                "{\"name\":\"Robô\",\"email\":\"r-extra@teste.dev\",\"password\":\"senhaforte1\",\"acceptTerms\":true}", 429);
        assertThat(blocked.get("message").asText()).contains("Tente de novo em 1 hora");
    }

    @Test
    void healthCheckParaAHospedagem() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void excluirContaApagaTudo() throws Exception {
        MockHttpSession s = newUser("null");
        launch(s, "DESPESA", 10_000, category(s, "Mercado"), "");
        call(s, delete("/api/profile"), "{\"confirm\":\"EXCLUIR\",\"password\":\"senhaforte1\"}", 204);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
    }
}
