package sz.genetics;

import sz.plan.PlanOperation;
import sz.production.Workplace;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public class Chromosome {
    private List<PlanOperation> planOperation;                  //плановые операции всего заказа
    private List<Workplace> workplaces;                         //лист рабочих мест

    public Chromosome(List<PlanOperation> planOperation, List<Workplace> workplaces) {
        this.planOperation = planOperation;
        this.workplaces = workplaces;
    }

    public List<PlanOperation> getPlanOperation() {
        return planOperation;
    }

    public List<Workplace> getWorkplaces() {
        return workplaces;
    }

    public void setPlanOperation(List<PlanOperation> planOperation) {
        this.planOperation = planOperation;
    }

    public void setWorkplaces(List<Workplace> workplaces) {
        this.workplaces = workplaces;
    }

//    public void resetWorkplaces() {
//        for (Workplace workplace : workplaces) {
//            workplace.setAvailability(LocalDateTime.parse("2020-01-29 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//            workplace.getDailyTasks().clear();
//        }
//    }

    public void resetWorkplaces() {
        for (Workplace workplace : workplaces) {
            workplace.getDailyTasks().removeIf(transferBatch -> {
                return transferBatch.getPlanOperation().getFactStart() == null ||
                        transferBatch.getPlanOperation().getFactFinish() != null ||
                        (transferBatch.getPlanStart() == null && transferBatch.getPlanFinish() == null);
            }) ;

            LocalDateTime currentTime = LocalDateTime.now();

            if (workplace.getDailyTasks().size() == 0) {
                workplace.setAvailability(currentTime);
            } else {
                workplace.setAvailability(workplace.getDailyTasks().stream().map(transferBatch -> transferBatch.getPlanFinish()).max(Comparator.naturalOrder()).get());
            }
        }
    }

}
