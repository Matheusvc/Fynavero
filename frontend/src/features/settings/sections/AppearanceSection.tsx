import { Segmented } from "@/components/ui/segmented";
import { useTheme, type ThemePreference } from "@/theme/theme";
import { Section } from "../shared";

export function AppearanceSection() {
  const { preference, setPreference } = useTheme();
  return (
    <Section id="aparencia" title="Aparência">
      <Segmented<ThemePreference>
        label="Tema"
        value={preference}
        onChange={setPreference}
        className="max-w-sm"
        options={[{ value: "light", label: "Claro" }, { value: "dark", label: "Escuro" }, { value: "system", label: "Sistema" }]}
      />
    </Section>
  );
}
