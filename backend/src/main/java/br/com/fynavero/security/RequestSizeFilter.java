package br.com.fynavero.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * Limite de tamanho do corpo das requisições (proteção contra abuso e "upload" de arquivos): a API só recebe
 * JSON, e o maior pedido legítimo é a importação de uma planilha (até 10 mil linhas, já lidas no navegador).
 * Upload de arquivo (multipart) não existe na API e é recusado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeFilter extends OncePerRequestFilter {
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String type = req.getContentType();
        if (type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/")) {
            res.sendError(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            return;
        }
        if (req.getContentLengthLong() > MAX_BYTES) {
            res.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return;
        }
        chain.doFilter(new Limited(req), res); // corpo sem Content-Length (chunked) também é contado
    }

    private static final class Limited extends HttpServletRequestWrapper {
        private ServletInputStream stream;

        Limited(HttpServletRequest req) {
            super(req);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new CountingStream(super.getInputStream());
            return stream;
        }
    }

    private static final class CountingStream extends ServletInputStream {
        private final ServletInputStream in;
        private long read;

        CountingStream(ServletInputStream in) {
            this.in = in;
        }

        private int count(int n) {
            if (n > 0 && (read += n) > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Pedido grande demais.");
            return n;
        }

        @Override public int read() throws IOException {
            int b = in.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException { return count(in.read(b, off, len)); }
        @Override public boolean isFinished() { return in.isFinished(); }
        @Override public boolean isReady() { return in.isReady(); }
        @Override public void setReadListener(ReadListener l) { in.setReadListener(l); }
    }
}
