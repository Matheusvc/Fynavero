import {
  Baby,
  Bike,
  BookOpen,
  Briefcase,
  Bus,
  Car,
  Carrot,
  Church,
  Coffee,
  CreditCard,
  Dumbbell,
  Ellipsis,
  Fuel,
  Gamepad2,
  Gift,
  GraduationCap,
  HandHeart,
  HeartPulse,
  House,
  Landmark,
  Laptop,
  ListChecks,
  type LucideIcon,
  Music,
  PartyPopper,
  PawPrint,
  PiggyBank,
  Pill,
  Plane,
  Receipt,
  Repeat,
  Scissors,
  Shirt,
  ShoppingBag,
  ShoppingCart,
  Smartphone,
  Sparkles,
  Tag,
  Target,
  TrendingDown,
  TrendingUp,
  TriangleAlert,
  Utensils,
  Wallet,
  Wifi,
  Wrench,
  Zap,
  Building2,
  Coins,
  ChartLine,
  Gem,
  Umbrella,
} from "lucide-react";
import type { SVGProps } from "react";

const ICONS: Record<string, LucideIcon> = {
  house: House,
  zap: Zap,
  wifi: Wifi,
  "shopping-cart": ShoppingCart,
  car: Car,
  "heart-pulse": HeartPulse,
  utensils: Utensils,
  "party-popper": PartyPopper,
  shirt: Shirt,
  sparkles: Sparkles,
  dumbbell: Dumbbell,
  plane: Plane,
  "credit-card": CreditCard,
  landmark: Landmark,
  repeat: Repeat,
  receipt: Receipt,
  baby: Baby,
  "graduation-cap": GraduationCap,
  "paw-print": PawPrint,
  church: Church,
  "hand-heart": HandHeart,
  "triangle-alert": TriangleAlert,
  gift: Gift,
  wallet: Wallet,
  briefcase: Briefcase,
  "piggy-bank": PiggyBank,
  tag: Tag,
  ellipsis: Ellipsis,
  carrot: Carrot,
  coffee: Coffee,
  bus: Bus,
  bike: Bike,
  fuel: Fuel,
  pill: Pill,
  smartphone: Smartphone,
  laptop: Laptop,
  "gamepad-2": Gamepad2,
  music: Music,
  "book-open": BookOpen,
  scissors: Scissors,
  wrench: Wrench,
  "shopping-bag": ShoppingBag,
  "building-2": Building2,
  coins: Coins,
  "chart-line": ChartLine,
  gem: Gem,
  umbrella: Umbrella,
  target: Target,
  "trending-up": TrendingUp,
  "trending-down": TrendingDown,
  "list-checks": ListChecks,
};

export const ICON_LABELS: Record<string, string> = {
  house: "Casa", zap: "Energia", wifi: "Internet", "shopping-cart": "Carrinho de compras", car: "Carro",
  "heart-pulse": "Saúde", utensils: "Talheres", "party-popper": "Festa", shirt: "Roupa", sparkles: "Brilho",
  dumbbell: "Academia", plane: "Avião", "credit-card": "Cartão", landmark: "Banco", repeat: "Repetição",
  receipt: "Recibo", baby: "Bebê", "graduation-cap": "Formatura", "paw-print": "Pet", church: "Igreja",
  "hand-heart": "Doação", "triangle-alert": "Alerta", gift: "Presente", wallet: "Carteira", briefcase: "Maleta",
  "piggy-bank": "Cofrinho", tag: "Etiqueta", carrot: "Cenoura", coffee: "Café", bus: "Ônibus", bike: "Bicicleta",
  fuel: "Combustível", pill: "Remédio", smartphone: "Celular", laptop: "Notebook", "gamepad-2": "Videogame",
  music: "Música", "book-open": "Livro", scissors: "Tesoura", wrench: "Ferramenta", "shopping-bag": "Sacola",
  "building-2": "Prédio", coins: "Moedas", "chart-line": "Gráfico", gem: "Joia", umbrella: "Guarda-chuva",
};

/** Ícones oferecidos ao criar ou editar uma categoria. */
export const ICON_CHOICES = Object.keys(ICONS).filter(
  (k) => !["ellipsis", "target", "trending-up", "trending-down", "list-checks"].includes(k),
);

export function Icon({ name, ...props }: { name: string } & SVGProps<SVGSVGElement>) {
  const C = ICONS[name] ?? Tag;
  return <C aria-hidden="true" {...props} />;
}

/** Ícone da categoria num círculo com a cor da categoria (cor acompanha a categoria). */
export function CategoryIcon({ icon, color, size = "md" }: { icon: string; color?: string; size?: "sm" | "md" | "lg" }) {
  const box = size === "sm" ? "size-8" : size === "lg" ? "size-12" : "size-10";
  const ic = size === "sm" ? "size-4" : size === "lg" ? "size-6" : "size-5";
  return (
    <span
      className={`${box} relative grid shrink-0 place-items-center overflow-hidden rounded-full bg-muted text-foreground`}
    >
      {color && (
        <span className="absolute inset-0 opacity-20" style={{ background: `var(--${color})` }} aria-hidden="true" />
      )}
      <Icon name={icon} className={`${ic} relative`} />
    </span>
  );
}
