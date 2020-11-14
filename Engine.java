package sz.genetics;

import sz.functionalmethods.Function;
import sz.plan.Plan;
import sz.plan.PlanOperation;
import sz.plan.AbstractTask;
import sz.production.Workplace;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Engine {
    //генетический алгоритм

    //Фитнесс-функция
    //https://www.researchgate.net/publication/242081421_A_JOB-SHOP_SCHEDULING_PROBLEM_JSSP_USING_GENETIC_ALGORITHM_GA
    public long fitnessFunction(List<Workplace> workplaces) {
        List<AbstractTask> operations = new ArrayList<>();

        workplaces.stream()
                .map(Workplace::getDailyTasks)
                .forEach(operations::addAll);

        operations.removeIf(transferBatch -> transferBatch.getPlanFinish() == null);

        operations.sort(new Comparator<AbstractTask>() {
            @Override
            public int compare(AbstractTask o1, AbstractTask o2) {
                return o1.getPlanFinish().compareTo(o2.getPlanFinish());
            }
        });

        LocalDateTime ldt = operations.get(operations.size() - 1).getPlanFinish();
        ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
        return zdt.toInstant().toEpochMilli() / 1000 / 60;
    }

    //Скрещивание(PPX crossover)
    //статья: https://www.researchgate.net/publication/2753293_On_Permutation_Representations_for_Scheduling_Problems
    public List<Chromosome> crossover(Chromosome parent1, Chromosome parent2){

        LinkedList<Integer> ordersNumbersParent1 = Function.encode(parent1);
        LinkedList<Integer> ordersNumbersParent2 = Function.encode(parent2);

        //массив - цепь хромосом
        boolean[] permutation = new boolean[parent1.getPlanOperation().size()];
        Random random = new Random();
        for (int i = 0; i < permutation.length; i++) {
            permutation[i] = random.nextBoolean();
        }

        LinkedList<Integer> ordersChild1 = cross(ordersNumbersParent1, ordersNumbersParent2, permutation);
        LinkedList<Integer> ordersChild2 = cross(ordersNumbersParent2, ordersNumbersParent1, permutation);

        Plan plan1 = new Plan();
        plan1.setOrders(parent1.getPlanOperation()
                .stream()
                .map(PlanOperation::getOrder)
                .distinct()
                .collect(Collectors.toList())
        );

        Plan plan2 = new Plan();
        plan2.setOrders(parent2.getPlanOperation()
                .stream()
                .map(PlanOperation::getOrder)
                .distinct()
                .collect(Collectors.toList())
        );

        List<PlanOperation> planOperationsChild1 = Function.decode(ordersChild1, Function.copyPlan(plan1));
        List<PlanOperation> planOperationsChild2 = Function.decode(ordersChild2, Function.copyPlan(plan2));

        Chromosome child1 = new Chromosome(planOperationsChild1, Function.copyWorkplace(parent1.getWorkplaces(), planOperationsChild1));
        Chromosome child2 = new Chromosome(planOperationsChild2, Function.copyWorkplace(parent2.getWorkplaces(), planOperationsChild2));

        List<Chromosome> children = new ArrayList<>();
        children.add(child1);
        children.add(child2);
        return children;
    }

    // Метод скрещивает двух родителей для создания потомка
    private LinkedList<Integer> cross(LinkedList<Integer> ordersNumbersParent1,
                                      LinkedList<Integer> ordersNumbersParent2,
                                      boolean[] permutation) {

        ordersNumbersParent1 = new LinkedList<>(ordersNumbersParent1);
        ordersNumbersParent2 = new LinkedList<>(ordersNumbersParent2);

        LinkedList<Integer> ordersChild = new LinkedList<>();

        for (boolean b : permutation) {

            int indexParent = -1;
            if (b) {
                indexParent = ordersNumbersParent1.pop();
                ordersChild.add(indexParent);
                ordersNumbersParent2.removeFirstOccurrence(indexParent);
            } else {
                indexParent = ordersNumbersParent2.pop();

                ordersChild.add(indexParent);
                ordersNumbersParent1.removeFirstOccurrence(indexParent);
            }
        }
        return ordersChild;
    }

    //Мутация
    public void mutation(Chromosome chromosome) {
        LinkedList<Integer> orders = Function.encode(chromosome);

        int pair = -1;
        pair = (int) (orders.size() * 0.05);

        for (int i = 0; i < pair; i++) {
            int firstIndex = -1;
            int secondIndex = -1;
            while (firstIndex == secondIndex) {
                firstIndex = (int) (Math.random() * orders.size());
                secondIndex = (int) (Math.random() * orders.size());
            }
            int indexElement = orders.get(firstIndex);
            orders.set(firstIndex, orders.get(secondIndex));
            orders.set(secondIndex, indexElement);
        }

        Plan plan = new Plan();
        plan.setOrders(chromosome.getPlanOperation()
                .stream()
                .map(PlanOperation::getOrder)
                .distinct()
                .collect(Collectors.toList())
        );
        List<PlanOperation> planOperations = Function.decode(orders, Function.copyPlan(plan));
        chromosome.setPlanOperation(planOperations);
    }

    //Селекция
    public List<Integer> selection(List<Long> rates) {
        long totalRate = rates.stream().mapToLong(f -> f).sum();
        List<Double> probList = new ArrayList<>();
        rates.forEach(aLong -> probList.add((double) aLong / totalRate));

        List<Double> wheel = new ArrayList<>();
        wheel.add(probList.get(0));
        for (int i = 1; i < probList.size(); i++) {
            wheel.add(wheel.get(i - 1) + probList.get(i));
        }

        List<Integer> indexes = new ArrayList<>();
        while (indexes.size() < 20) {
            double p = Math.random();
            for (int j = wheel.size() - 1; j >= 0; j--) {
                if (p >= wheel.get(j)) {
                    if (!indexes.contains(j)) indexes.add(j + 1);
                    break;
                }
            }
        }
        return indexes;
    }




}
