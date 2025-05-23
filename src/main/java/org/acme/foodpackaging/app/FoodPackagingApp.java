package org.acme.foodpackaging.app;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import org.acme.foodpackaging.bootstrap.DataExporter;
import org.acme.foodpackaging.bootstrap.ImportOrderData;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.solver.FoodPackagingConstraintProvider;

import java.io.IOException;
import java.time.Duration;

public class FoodPackagingApp {

    public static void main(String[] args) throws IOException {

        if(args.length!=0) {
            SolverFactory<PackagingSchedule> solverFactory = SolverFactory.create(new SolverConfig()
                    .withSolutionClass(PackagingSchedule.class)
                    .withEntityClasses(Job.class, Line.class)
                    .withConstraintProviderClass(FoodPackagingConstraintProvider.class)
                    // The solver runs only for 5 seconds on this small dataset.
                    // It's recommended to run for at least 5 minutes ("5m") otherwise.
                    .withTerminationSpentLimit(Duration.ofSeconds(5)));

            ImportOrderData importData = new ImportOrderData();

            PackagingSchedule problem = importData.scheduleInitializer(args[0]);

            DataExporter exporter = new DataExporter(args[0]);
            exporter.exportAsYaml(problem);
            // Solve the problem
            Solver<PackagingSchedule> solver = solverFactory.buildSolver();
            PackagingSchedule solution = solver.solve(problem);

            HardMediumSoftLongScore score = problem.getScore();
            SolutionManager< PackagingSchedule, HardMediumSoftScore> solutionManager = SolutionManager.create(solverFactory);
            ScoreExplanation< PackagingSchedule, HardMediumSoftScore> scoreExplanation = solutionManager.explain(solution);

            exporter.exportAsJson(solution, score);
            
        }
        else {
            System.out.println("No date set!");
        }

    }
}