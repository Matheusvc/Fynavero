package br.com.fynavero;

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

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private final String month = YearMonth.from(today).toString();

    private JsonNode call(MockHttpSession s, MockHttpServletRequestBuilder req, Object body, int expected) throws Exception {
        req.session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(body instanceof String str ? str : json.writeValueAsString(body));
        var res = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
        String text = res.getContentAsString();
        return text.isEmpty() ? null : json.readTree(text);
    }

    /** Cria conta, conclui o onboarding e devolve a sessão. */
    private MockHttpSession newUser(String contributionJson) throws Exception {
        MockHttpSession s = new MockHttpSession();
        String email = "p" + UUID.randomUUID().toString().substring(0, 8) + "@teste.dev";
        call(s, post("/api/auth/register"),
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

    @Test
    void excluirContaApagaTudo() throws Exception {
        MockHttpSession s = newUser("null");
        launch(s, "DESPESA", 10_000, category(s, "Mercado"), "");
        call(s, delete("/api/profile"), "{\"confirm\":\"EXCLUIR\",\"password\":\"senhaforte1\"}", 204);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
    }
}
