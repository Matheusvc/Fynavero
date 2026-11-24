-- Vários objetivos por pessoa (lista separada por vírgula).
ALTER TABLE profiles ADD COLUMN objectives VARCHAR(100);
UPDATE profiles SET objectives = objective WHERE objective IS NOT NULL;
ALTER TABLE profiles DROP COLUMN objective;

-- Categoria de receita que representa empréstimo recebido (entra no saldo, não na renda).
ALTER TABLE categories ADD COLUMN loan BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO categories (user_id, name, kind, group_name, icon, color, sort_order, hidden, loan, created_at)
SELECT p.id, 'Empréstimo', 'RECEITA', 'Receitas', 'landmark', 'chart-3', 99, FALSE, TRUE, CURRENT_TIMESTAMP
FROM profiles p
WHERE p.onboarding_done = TRUE
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.user_id = p.id AND c.kind = 'RECEITA' AND LOWER(c.name) = 'empréstimo');

UPDATE categories SET loan = TRUE WHERE kind = 'RECEITA' AND LOWER(name) = 'empréstimo';
