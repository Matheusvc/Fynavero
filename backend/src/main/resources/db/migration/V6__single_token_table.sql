-- Um único mecanismo para links enviados por e-mail: entrar sem senha, redefinir senha, confirmar e-mail e
-- "Marcar como paga". ref_id aponta para o objeto do link (ex.: o lançamento a pagar).
-- Links que estavam pendentes nas tabelas antigas deixam de valer; basta pedir um novo.
ALTER TABLE account_tokens ADD COLUMN ref_id BIGINT;

DROP TABLE magic_link_tokens;
DROP TABLE action_tokens;
