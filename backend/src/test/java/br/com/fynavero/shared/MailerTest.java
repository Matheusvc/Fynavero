package br.com.fynavero.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailerTest {

    @Test
    void separaNomeEEnderecoDoRemetente() {
        assertThat(Mailer.parseAddress("Fynavero <nao-responda@fynavero.com.br>"))
                .containsExactly("Fynavero", "nao-responda@fynavero.com.br");
        assertThat(Mailer.parseAddress(" \"Fynavero\"  < a@b.com > ")).containsExactly("Fynavero", "a@b.com");
        assertThat(Mailer.parseAddress("a@b.com")).containsExactly("", "a@b.com");
    }
}
