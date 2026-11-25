package br.com.fynavero.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlProcessorTest {

    @Test
    void converteUrlDaHospedagemParaJdbc() {
        var p = DatabaseUrlProcessor.convert("postgres://fyn:s%40nha@db.render.com:5432/fynavero?sslmode=require", null, null);
        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://db.render.com:5432/fynavero?sslmode=require")
                .containsEntry("spring.datasource.username", "fyn")
                .containsEntry("spring.datasource.password", "s@nha");
    }

    @Test
    void usuarioInformadoSeparadoTemPrioridade() {
        var p = DatabaseUrlProcessor.convert("postgresql://a:b@host/banco", "outro", "segredo");
        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://host/banco")
                .doesNotContainKeys("spring.datasource.username", "spring.datasource.password");
    }

    @Test
    void urlJdbcOuVaziaNaoMuda() {
        assertThat(DatabaseUrlProcessor.convert("jdbc:postgresql://host/banco", null, null)).isEmpty();
        assertThat(DatabaseUrlProcessor.convert(null, null, null)).isEmpty();
    }
}
