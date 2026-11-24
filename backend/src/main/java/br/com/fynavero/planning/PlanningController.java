package br.com.fynavero.planning;

import br.com.fynavero.config.AppProperties;
import br.com.fynavero.planning.PlanningDtos.BudgetDto;
import br.com.fynavero.planning.PlanningDtos.GoalDto;
import br.com.fynavero.planning.PlanningDtos.GoalReq;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static br.com.fynavero.common.Formats.parseMonth;

@RestController
@RequestMapping("/api")
public class PlanningController {
    private final BudgetService budgets;
    private final GoalService goals;
    private final AppProperties props;

    public PlanningController(BudgetService budgets, GoalService goals, AppProperties props) {
        this.budgets = budgets;
        this.goals = goals;
        this.props = props;
    }

    /** Limites do mês por categoria de despesa, do mais perto de estourar para o mais longe. */
    @GetMapping("/budgets")
    public List<BudgetDto> budgets(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month) {
        return budgets.status(u.id(), parseMonth(month, props.zoneId()));
    }

    @GetMapping("/goals")
    public List<GoalDto> goals(@AuthenticationPrincipal AuthUser u) {
        return goals.list(u.id());
    }

    @PostMapping("/goals")
    public GoalDto create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody GoalReq body) {
        return goals.create(u.id(), body);
    }

    @PutMapping("/goals/{id}")
    public GoalDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody GoalReq body) {
        return goals.update(u.id(), id, body);
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        goals.remove(u.id(), id);
        return ResponseEntity.noContent().build();
    }
}
