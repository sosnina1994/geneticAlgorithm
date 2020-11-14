package sz.functionalmethods;

import sz.genetics.Chromosome;
import sz.plan.*;
import sz.production.Workplace;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class Function {
    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    //создание копии плана(копирование операций в констукторе заказов)
    public static Plan copyPlan(Plan plan) {
        Plan copiedPlan = new Plan();
        List<Order> copiedOrders = new ArrayList<>();

        for (Order order : plan.getOrders()) {
            copiedOrders.add(new Order(order));
        }
        copiedPlan.setOrders(copiedOrders);
        return copiedPlan;
    }

    //создание копии рабочих мест
    public static List<Workplace> copyWorkplace(List<Workplace> workplaces, List<PlanOperation> planOperations) {
        List<Workplace> copiedWorkplaces = new ArrayList<>();

        for (Workplace wp : workplaces) {
            copiedWorkplaces.add(new Workplace(wp, planOperations));
        }
        return copiedWorkplaces;
    }

    // Метод преобразует лист плановых операций хромосомы родителя в последовательность номеров заказов
    public static LinkedList<Integer> encode(Chromosome parent) {


        return parent.getPlanOperation()
                .stream()
                .map(planOperation -> planOperation.getOrder().getNumber())
                .collect(Collectors.toCollection(LinkedList::new));

    }

    // метод преобразует последовательность из номеров заказов в лист плановых операций
    public static List<PlanOperation> decode(LinkedList<Integer> ordersChild, Plan areaPlan) {

        List<PlanOperation> planOperationsChild = new ArrayList<>();    //Плановые операции потомка

        for (Integer orderNumber : ordersChild) {
            Order order = areaPlan.getOrderByNumber(orderNumber);       //вернуть заказ по номеру

            PlanOperation po = order.getPlanOperations().get(0);
            planOperationsChild.add(po);
            order.getPlanOperations().remove(po);
        }
        planOperationsChild.forEach(po -> po.getOrder().getPlanOperations().add(po));

        return planOperationsChild;
    }

    public  static String saveResult(List<Long> resultMinFF) {
        File csv1 = new File("result.csv");
        try (FileWriter writer1 = new FileWriter(csv1)) {
            StringBuilder sb1 = new StringBuilder();
            for (long value : resultMinFF) {
                sb1.append(value).append("\n");
            }

            sb1.deleteCharAt(sb1.length() - 1);
            writer1.write(sb1.toString());

            return sb1.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String saveChart(Chromosome solution, String fileName) {
        File csv = new File(fileName);
        try (FileWriter writer = new FileWriter(csv)) {
            StringBuilder sb = new StringBuilder("gantt,");
            List<Order> ordersList = solution.getPlanOperation().stream()
                    .map(PlanOperation::getOrder)
                    .distinct()
                    .collect(Collectors.toList());

            for (Order order : ordersList) {
                sb.append(order.getTechCard().getCipher()).append(";");

            }
            sb.replace(sb.length() - 1, sb.length(), "\n");
            writer.write(sb.toString());
            solution.getWorkplaces().sort(Comparator.comparingInt(Workplace::getInvNumber));

            StringBuilder sb2 = new StringBuilder();
            Iterator<Workplace> iterator = solution.getWorkplaces().iterator();
            while (iterator.hasNext()) {
                Workplace w = iterator.next();
                if (w.getDailyTasks().size() == 0) {
                    continue;
                }

                for (AbstractTask task : w.getDailyTasks()) {
                    sb2.append(dateTimeFormatter.format(task.getPlanStart()));
                    sb2.append(",")
                            .append(dateTimeFormatter.format(task.getPlanFinish()))
                            .append(",#")
                            .append(task.getPlanOperation().getOrder().getColor())
                            .append(",")
                            .append(task.getPlanOperation().getOrder().getTechCard().getCipher())
                            .append(",")
                            .append(w.getInvNumber())
                            .append(",")
                            .append(task.getPlanOperation().getNumber())
                            .append(",")
                            .append(task.getType() == TaskType.ADJUSTMENT ? 1 : task.getBatch());
                    sb2.append("\n");
                }
            }
            writer.write(sb2.toString());
            //System.out.println(sb.toString() + sb2.toString());

            return sb.toString() + sb2.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getAvailableForStart(Chromosome solution) {
        Set<PlanOperation> availableForStart = solution.getWorkplaces()
                .stream()
                .filter(workplace -> workplace.getDailyTasks().size() > 0)
                .map(workplace -> workplace.getDailyTasks().get(0))
                .filter(task -> task.getPlanOperation().getFactStart() == null)
                .filter(task -> {
                    PlanOperation prevOp = task.getPlanOperation().getOrder().getPrevPlanOperation(task.getPlanOperation());
                    if (prevOp == null) {
                        return true;
                    } else {
                        int batchSum = prevOp.getParallelTask().getBatch() + prevOp.getSequentialTask().getBatch();
                        return prevOp.getFactStart() != null && batchSum < prevOp.getOrder().getBatch();
                    }
                })
                .map(task -> task.getPlanOperation())
                .collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();
        if (availableForStart.size() > 0) {
            for (PlanOperation po : availableForStart) {
                sb.append(po.getOrder().getNumber())
                        .append(" (")
                        .append(po.getOrder().getTechCard().getCipher())
                        .append(")#")
                        .append(po.getNumber())
                        .append("\n");
            }
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

    public static String getAvailableForFinish(Chromosome solution) {
        Set<PlanOperation> availableForFinish = new HashSet<>();
        solution.getWorkplaces().forEach(workplace -> {
            workplace.getDailyTasks().stream()
                    .filter(transferBatch -> transferBatch.getPlanOperation().getFactStart() != null && transferBatch.getPlanOperation().getFactFinish() == null)
                    .forEach(transferBatch -> availableForFinish.add(transferBatch.getPlanOperation()));
        });

        StringBuilder sb = new StringBuilder();
        if (availableForFinish.size() > 0) {
            for (PlanOperation po : availableForFinish) {
                sb.append(po.getOrder().getNumber())
                        .append(" (")
                        .append(po.getOrder().getTechCard().getCipher())
                        .append(")#")
                        .append(po.getNumber())
                        .append("#")
                        .append(po.getOrder().getPlanOperations().indexOf(po) != 0 ?
                                po.getParallelTask().getBatch() :
                                po.getSequentialTask().getBatch())
                        .append("\n");
            }
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }
}
