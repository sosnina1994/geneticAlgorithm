package sz;

import sz.functionalmethods.Function;
import sz.generator.Generator;
import sz.genetics.Chromosome;
import sz.genetics.Engine;
import sz.plan.Order;
import sz.plan.Plan;
import sz.plan.PlanOperation;
import sz.production.Area;
import sz.production.Workplace;
import sz.technologicalProcess.TechCardArchive;
import sz.technologicalProcess.TechOperation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class MES {

    LocalDateTime beginningTime = LocalDateTime.now();//нулевая точка
    private LocalDateTime currentTime = LocalDateTime.from(beginningTime); //текущая точка. на момент старта совпадает с нулем

    private static final int POPULATION_SIZE = 200;

    private static Chromosome bestSolution;

    public static void main(String[] args) {
        MES mes = new MES();
        Engine engine = new Engine();

        List<Workplace> workplaces = Generator.create();
        TechCardArchive techCardArchive = new TechCardArchive();
        List<Order> orderList = Generator.staticGenerate(workplaces, techCardArchive,
                LocalDateTime.now());

        //List<Order> orderList = Generator.generate(8, techCardArchive, workplaces,
        // LocalDateTime.parse("2020-01-02 00:00:00", Function.dateTimeFormatter)));

        Plan workPlan = new Plan();
        workPlan.setOrders(orderList);

        Area area = new Area(5);

        Comparator<PlanOperation> c = new Comparator<PlanOperation>() {
            @Override
            public int compare(PlanOperation o1, PlanOperation o2) {
                return o1.getSequentialTask().getPlanStart().compareTo(o2.getSequentialTask().getPlanStart());
            }
        };

        List<PlanOperation> operations = new ArrayList<>();
        workPlan.getOrders()
                .stream()
                .map(Order::getPlanOperations)
                .forEach(operations::addAll);

        List<Long> resultMinFF = new ArrayList<>();

        Chromosome first = new Chromosome(operations, workplaces);

        List<Chromosome> population = mes.generateStartPopulation(first, POPULATION_SIZE);

        Chromosome bestSolution = mes.startAlgorithm(population, resultMinFF, engine);

        //csv на лист лучших значений
        String resultFF = Function.saveResult(resultMinFF);



        bestSolution.resetWorkplaces();
        mes.dispense(bestSolution);
        String plan = Function.saveChart(bestSolution, "chart1.csv");

        String s = resultFF + ":::" + plan;


        //Тест
        for (PlanOperation po : bestSolution.getPlanOperation()) {
            if (po.getOrder().getTechCard().getCipher().equals("ПИКВ400")) {
                PlanOperation planOperation = po.getOrder().getPlanOperations().get(0);

                mes.takeToWork(planOperation, LocalDateTime.parse("2020-03-16 00:00:00", Function.dateTimeFormatter));
                break;
            }
        }

        bestSolution.resetWorkplaces();
        population = mes.generateStartPopulation(bestSolution, POPULATION_SIZE);
        resultMinFF = new ArrayList<>();
        bestSolution = mes.startAlgorithm(population, resultMinFF, engine);
        Function.saveChart(bestSolution, "chart10.csv");

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        for (PlanOperation po : bestSolution.getPlanOperation()) {
            if (po.getOrder().getTechCard().getCipher().equals("ПИКВ400")) {
                PlanOperation planOperation = po.getOrder().getPlanOperations().get(0);

                mes.confirmWork(planOperation, 30, LocalDateTime.parse("2020-03-17 00:00:00", Function.dateTimeFormatter));
                break;
            }
        }
        bestSolution.resetWorkplaces();
        population = mes.generateStartPopulation(bestSolution, POPULATION_SIZE);
        resultMinFF = new ArrayList<>();
        bestSolution = mes.startAlgorithm(population, resultMinFF, engine);
        Function.saveChart(bestSolution, "chart50.csv");

        //запустить алгоритм при взятии в работу
        bestSolution.resetWorkplaces();
        mes.dispense(bestSolution);

        Function.saveChart(bestSolution, "chart2.csv");

    }

    public static void setBestSolution(Chromosome bestSolution) {
        MES.bestSolution = bestSolution;
    }

    public static Chromosome getBestSolution() {
        return bestSolution;
    }

    public void takeToWork(PlanOperation po, LocalDateTime start) {

        //LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime currentTime = LocalDateTime.from(start);
        po.setFactStart(currentTime);

        LocalDateTime mainStart;

        if (po.getAdjustmentTask() != null) {
            po.getAdjustmentTask().setPlanStart(po.getFactStart());
            po.getAdjustmentTask().setPlanFinish(po.getFactStart().plusSeconds(po.getAdjustmentTask().getDuration()));
            mainStart = po.getAdjustmentTask().getPlanFinish();
        } else mainStart = po.getFactStart();


        long opDuration = po.getOrder().getTechOperation(po.getNumber()).getDuration();
        int parallelBatch = po.getParallelTask().getBatch();
        int sequenceBatch = po.getSequentialTask().getBatch();

        if (po.getSequentialTask().getBatch() > 0 && po.getParallelTask().getBatch() > 0) {
            po.getParallelTask().setPlanStart(mainStart);
            po.getParallelTask().setPlanFinish(mainStart.plusSeconds(opDuration * parallelBatch));

            if (currentTime.isAfter(po.getParallelTask().getPlanStart())) {
                po.getSequentialTask().setPlanStart(po.getParallelTask().getPlanFinish());
                po.getSequentialTask().setPlanFinish(mainStart.plusSeconds(opDuration * (parallelBatch + sequenceBatch)));
            }

        } else {
            if (po.getParallelTask().getBatch() > 0) {
                po.getParallelTask().setPlanStart(mainStart);
                po.getParallelTask().setPlanFinish(mainStart.plusSeconds(opDuration * parallelBatch));
                po.getSequentialTask().setPlanStart(null);
                po.getSequentialTask().setPlanFinish(null);
            } else {
                po.getSequentialTask().setPlanStart(mainStart);
                po.getSequentialTask().setPlanFinish(mainStart.plusSeconds(opDuration * sequenceBatch));
                po.getParallelTask().setPlanStart(null);
                po.getParallelTask().setPlanFinish(null);
            }
        }
    }


    public void confirmWork(PlanOperation po, int n, LocalDateTime confirmTime) throws IllegalArgumentException {
        if (po.getAdjustmentTask() != null) {
            po.getAdjustmentTask().setPlanStart(null);
            po.getAdjustmentTask().setPlanFinish(null);
        }

        if (po.getOrder().getPlanOperations().indexOf(po) != 0) {
            if (n > po.getParallelTask().getBatch()) {
                throw new IllegalArgumentException("Incorrect confirm number");
            }
            po.getParallelTask().decrease(n);
        } else {
            if (n > po.getSequentialTask().getBatch()) {
                throw new IllegalArgumentException("Incorrect confirm number");
            }
            po.getSequentialTask().decrease(n);
        }

        long opDuration = po.getOrder().getTechOperation(po.getNumber()).getDuration();

        if (po.getSequentialTask().getBatch() == 0 && po.getParallelTask().getBatch() == 0) {
            po.setFactFinish(confirmTime);
            po.getParallelTask().setPlanStart(null);
            po.getParallelTask().setPlanFinish(null);
            po.getSequentialTask().setPlanStart(null);
            po.getSequentialTask().setPlanFinish(null);
        } else {
            if (po.getSequentialTask().getBatch() > 0 && po.getParallelTask().getBatch() > 0) {
                int remainder = po.getParallelTask().getBatch();
                po.getParallelTask().setPlanStart(confirmTime);
                po.getParallelTask().setPlanFinish(confirmTime.plusSeconds(remainder * opDuration));

                if (po.getParallelTask().getPlanFinish().isAfter(po.getSequentialTask().getPlanStart())) {
                    po.getSequentialTask().setPlanStart(po.getParallelTask().getPlanFinish());
                    LocalDateTime seqStart = po.getSequentialTask().getPlanStart();
                    po.getSequentialTask().setPlanFinish(seqStart.plusSeconds(po.getSequentialTask().getBatch() * opDuration));
                } else {
                    PlanOperation prevOperation = po.getOrder().getPrevPlanOperation(po);
                    LocalDateTime prevFinish;
                    if (prevOperation.getSequentialTask().getBatch() != 0) {
                        prevFinish = prevOperation.getSequentialTask().getPlanFinish();
                    } else {
                        prevFinish = prevOperation.getParallelTask().getPlanFinish();
                    }

                    if (prevFinish.isAfter(po.getParallelTask().getPlanFinish())) {
                        po.getSequentialTask().setPlanStart(prevFinish);
                    } else {
                        po.getSequentialTask().setPlanStart(po.getParallelTask().getPlanFinish());
                    }

                    LocalDateTime seqStart = po.getSequentialTask().getPlanStart();
                    po.getSequentialTask().setPlanFinish(seqStart.plusSeconds(po.getSequentialTask().getBatch() * opDuration));
                }
            } else {
                if (po.getSequentialTask().getBatch() > 0) {
                    int remainder = po.getSequentialTask().getBatch();
                    po.getSequentialTask().setPlanStart(confirmTime);
                    po.getSequentialTask().setPlanFinish(confirmTime.plusSeconds(remainder * opDuration));
                } else {
                    int remainder = po.getParallelTask().getBatch();
                    po.getParallelTask().setPlanStart(confirmTime);
                    po.getParallelTask().setPlanFinish(confirmTime.plusSeconds(remainder * opDuration));
                }
            }
        }

        try {
            LocalDateTime planFinish;
            if (po.getSequentialTask().getBatch() != 0) {
                planFinish = po.getSequentialTask().getPlanFinish();
            } else if (po.getParallelTask().getBatch() != 0) {
                planFinish = po.getParallelTask().getPlanFinish();
            } else {
                planFinish = po.getFactFinish();
            }

            PlanOperation nextOperation = po.getOrder().getNextPlanOperation(po);

            long nextDuration = po.getOrder().getTechOperation(po.getNumber()).getNextOperation().getDuration();
            nextOperation.getParallelTask().increase(n);
            nextOperation.getSequentialTask().decrease(n);

            long b = Duration.between(confirmTime, planFinish).get(ChronoUnit.SECONDS);
            //если длительность изготовления партии >= участка времени, то планируем вправо
            // иначе планируем влево
            if (nextDuration * n >= b) nextOperation.setParallelPlanToLeft(false);
            else nextOperation.setParallelPlanToLeft(true);

            if (nextOperation.getFactStart() != null) {
                LocalDateTime nextParallelStart = nextOperation.getParallelTask().getPlanStart();
                nextOperation.getParallelTask().setPlanFinish(nextParallelStart.plusSeconds(nextOperation.getParallelTask().getBatch() * nextDuration));

                nextOperation.getSequentialTask().setPlanStart(planFinish);
                LocalDateTime nextSeqStart = nextOperation.getSequentialTask().getPlanStart();
                nextOperation.getSequentialTask().setPlanFinish(nextSeqStart.plusSeconds(nextOperation.getSequentialTask().getBatch() * nextDuration));

                if (nextOperation.getParallelTask().getPlanFinish().isAfter(nextSeqStart)) {
                    nextOperation.getSequentialTask().setPlanStart(nextOperation.getParallelTask().getPlanFinish());

                    nextSeqStart = nextOperation.getSequentialTask().getPlanStart();
                    nextOperation.getSequentialTask().setPlanFinish(nextSeqStart.plusSeconds(nextOperation.getSequentialTask().getBatch() * nextDuration));
                }
            }
        } catch (Exception e) {}
    }

    public List<Chromosome> generateStartPopulation(Chromosome chrom, int populationSize) {
        List<Chromosome> population = new ArrayList<>();

        Plan plan = new Plan();
        plan.setOrders(chrom.getPlanOperation()
                .stream()
                .map(PlanOperation::getOrder)
                .distinct()
                .collect(Collectors.toList())
        );

        LinkedList<Integer> encodedChromosome = Function.encode(chrom);

        for (int i = 0; i < populationSize; i++) {
            Collections.shuffle(encodedChromosome);
            List<PlanOperation> po = Function.decode(encodedChromosome, Function.copyPlan(plan));
            Chromosome chromosome = new Chromosome(po, Function.copyWorkplace(chrom.getWorkplaces(), po));
            dispense(chromosome);
            population.add(chromosome);
        }
        return population;
    }



    public Chromosome startAlgorithm(List<Chromosome> population, List<Long> resultMinFF, Engine engine) {
        long min = Long.MAX_VALUE;
        int minIndex = 0;


        List<Long> rates = new ArrayList<>();
        for (Chromosome chromosome : population) {
            rates.add(engine.fitnessFunction(chromosome.getWorkplaces()));
        }

        for (int count = 0; count < 300; count++) {
            List<Integer> indexes = engine.selection(rates);

            for (int i = 0; i < indexes.size(); i += 2) {
                List<Chromosome> children = engine.crossover(population.get(indexes.get(i)),
                        population.get(indexes.get(i + 1)));

                for (Chromosome child : children) {
                    long max = Collections.max(rates);
                    int maxIndex = rates.indexOf(max);
                    child.resetWorkplaces();
                    dispense(child);
                    long childFitFunc = engine.fitnessFunction(child.getWorkplaces());
                    if (childFitFunc <= max) {
                        population.get(maxIndex).setPlanOperation(child.getPlanOperation());
                        rates.set(maxIndex, 0L);
                    }
                }
            }

            double d = Math.random();
            //if (d > 0.5) {
            for (int i = 0; i < 2; i++) {
                int mutationIndex = (int) (Math.random() * population.size());
                engine.mutation(population.get(mutationIndex));
            }
            //}

            for (Chromosome chromosome : population) {
                chromosome.resetWorkplaces();
                dispense(chromosome);
            }

            rates.clear();
            for (Chromosome chromosome : population) {
                rates.add(engine.fitnessFunction(chromosome.getWorkplaces()));
            }

            min = Collections.min(rates);
            minIndex = rates.indexOf(min);
            resultMinFF.add(min);
        }

        System.out.println("BestFunction: " + min);
        return population.get(minIndex);
    }





    //метод по распределению списка плановых операций по рабочим местам
    public void dispense(Chromosome chromosome) {
        for (PlanOperation po : chromosome.getPlanOperation()) {

            if (po.getFactFinish() != null) {
                continue;
            }
            TechOperation to = po.getOrder().getTechOperation(po.getNumber());

            chromosome.getWorkplaces().forEach(Workplace::fixWorkplaceAvailability);
            chromosome.getWorkplaces().sort(new Comparator<Workplace>() {
                @Override
                public int compare(Workplace o1, Workplace o2) {
                    return o1.getAvailability().compareTo(o2.getAvailability());
                }
            });

            for (Workplace wp : chromosome.getWorkplaces()) {
                if (to.isWorkplacesPermitted(wp)) {
                    boolean check = chromosome.getWorkplaces()
                            .stream()
                            .map(workplace -> workplace.getDailyTasks())
                            .anyMatch(tasks -> tasks.contains(po.getParallelTask()) || tasks.contains(po.getSequentialTask()));

                    if (check && !(wp.getDailyTasks().contains(po.getParallelTask()) || wp.getDailyTasks().contains(po.getSequentialTask()))) {
                        continue;
                    }

                    PlanOperation prevOperation;
                    try {
                        int currentPlanOperationIndex = po.getOrder().getPlanOperations().indexOf(po);
                        //предыдущая операция
                        prevOperation = po.getOrder().getPlanOperations().get(currentPlanOperationIndex - 1);

                    } catch (Exception e) {
                        prevOperation = null;
                    }

                    LocalDateTime prevOperationPlanStart = null;
                    LocalDateTime prevOperationPlanFinish = null;
                    if (prevOperation != null) {
                        if (prevOperation.getParallelTask().getBatch() > 0) {
                            prevOperationPlanStart = prevOperation.getParallelTask().getPlanStart();
                        } else if (prevOperation.getSequentialTask().getBatch() > 0) {
                            prevOperationPlanStart = prevOperation.getSequentialTask().getPlanStart();
                        } else prevOperationPlanStart = prevOperation.getFactFinish();

                        if (prevOperation.getSequentialTask().getBatch() > 0) {
                            prevOperationPlanFinish = prevOperation.getSequentialTask().getPlanFinish();
                        } else if (prevOperation.getParallelTask().getBatch() > 0) {
                            prevOperationPlanFinish = prevOperation.getParallelTask().getPlanFinish();
                        } else prevOperationPlanFinish = prevOperation.getFactFinish();
                    }

                    if (po.getParallelTask().getBatch() > 0 && !wp.getDailyTasks().contains(po.getParallelTask())) {
                        if (po.isParallelPlanToLeft()) {
                            if (prevOperationPlanFinish != null) {
                                long d = po.getOrder().getTechOperation(po.getNumber()).getDuration();
                                long adjDuration = 0;
                                if (po.getAdjustmentTask() != null) {
                                    adjDuration = po.getAdjustmentTask().getDuration();
                                }

                                LocalDateTime a = prevOperationPlanFinish.minusSeconds(d * po.getParallelTask().getBatch() + adjDuration);

                                if (wp.getAvailability().isBefore(a)) {
                                    wp.setAvailability(a);
                                }
                            }
                        } else {
                            if (prevOperationPlanStart != null && prevOperationPlanStart.isAfter(wp.getAvailability())) {
                                wp.setAvailability(prevOperationPlanStart);
                            }
                        }

                        wp.fixWorkplaceAvailability();


                        if (po.getAdjustmentTask() != null) {
                            wp.fixThirdShift();
                            wp.addTask(po.getAdjustmentTask());
                            po.getAdjustmentTask().setPlanStart(wp.getAvailability());
                            wp.setAvailability(wp.getAvailability().plusSeconds(po.getAdjustmentTask().getDuration()));
                            po.getAdjustmentTask().setPlanFinish(wp.getAvailability());
                        }
                        wp.addTask(po.getParallelTask());
                        po.getParallelTask().setPlanStart(wp.getAvailability());
                        wp.setAvailability(wp.getAvailability().plusSeconds(to.getDuration() * po.getParallelTask().getBatch()));
                        po.getParallelTask().setPlanFinish(wp.getAvailability());
                    }



                    if (prevOperationPlanFinish != null && prevOperationPlanFinish.isAfter(wp.getAvailability())) {
                        if (po.getAdjustmentTask() != null) {
                            LocalDateTime adjPlanStart = prevOperationPlanFinish.minusSeconds(po.getAdjustmentTask().getDuration());
                            if (adjPlanStart.isAfter(wp.getAvailability())) {
                                wp.setAvailability(adjPlanStart);
                            }
                        } else wp.setAvailability(prevOperationPlanFinish);
                    }

                    wp.fixWorkplaceAvailability();

                    if (!wp.getDailyTasks().contains(po.getSequentialTask())) {
                        if (po.getAdjustmentTask() != null && !wp.getDailyTasks().contains(po.getAdjustmentTask()) ) {
                            wp.fixThirdShift();

                            wp.addTask(po.getAdjustmentTask());
                            po.getAdjustmentTask().setPlanStart(wp.getAvailability());
                            wp.setAvailability(wp.getAvailability().plusSeconds(po.getAdjustmentTask().getDuration()));
                            po.getAdjustmentTask().setPlanFinish(wp.getAvailability());
                        }

                        wp.addTask(po.getSequentialTask());
                        po.getSequentialTask().setPlanStart(wp.getAvailability());

                        wp.setAvailability(
                                wp.getAvailability().plusSeconds(to.getDuration() * po.getSequentialTask().getBatch())
                        );
                        po.getSequentialTask().setPlanFinish(wp.getAvailability());
                    }

                    LocalDateTime currentFinish = po.getSequentialTask().getBatch() > 0 ?
                            po.getSequentialTask().getPlanFinish() :
                            po.getParallelTask().getPlanFinish();
                    if (wp.getAvailability().isAfter(currentFinish)) wp.setAvailability(currentFinish);

                    break;
                }
            }
        }
    }
}