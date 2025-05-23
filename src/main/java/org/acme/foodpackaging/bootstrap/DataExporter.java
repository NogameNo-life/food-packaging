package org.acme.foodpackaging.bootstrap;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.WorkCalendar;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DataExporter {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String date;
    public DataExporter(String date){
       this.date =date;
    }

    public void exportAsJson(PackagingSchedule solution, HardMediumSoftLongScore totalScore){
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("TotalMatch", totalScore);
        jsonMap.put("WorkCalendar", solution.getWorkCalendar());
        jsonMap.put("Lines", solution.getLines());
        jsonMap.put("Jobs", solution.getJobs());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String filePath = "src/main/resources/exportFiles/" + this.date + "Export.json";
        File file = new File(filePath);

        try {
            // Создаем все необходимые родительские директории
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonMap);
            System.out.println("JSON файл успешно создан: " + filePath);
        } catch (IOException e) {
            System.err.println("Ошибка при создании JSON файла: " + e.getMessage());
        }
    }

    public void exportAsYaml(PackagingSchedule problem) {
        Map<String, Object> yamlMap = new HashMap<>();
        WorkCalendar calendar = problem.getWorkCalendar();
        yamlMap.put("WorkCalendar", calendar.toString());
        yamlMap.put("Products", problem.getProducts());
        yamlMap.put("Jobs", problem.getJobs());
        yamlMap.put("Lines", problem.getLines());

        String filePath = "src/main/resources/importFiles/" + this.date + "Import.yaml";

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.registerModule(new JavaTimeModule());
        yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        File file = new File(filePath);

        try {
            // Создаем все необходимые родительские директории
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            yamlMapper.writeValue(new File(filePath),yamlMap);
            System.out.println("YAML файл успешно создан: " + filePath);
        } catch (IOException e) {
            System.err.println("Ошибка при создании YAML файла: " + e.getMessage());
        }
    }

    private List<JobRecord> getJobList(List<Job> jobs, List<Line> lines){

        List<JobRecord> jobRecordList = new ArrayList<>(jobs.size());

       for(Job job : jobs){

            jobRecordList.add(new JobRecord(
                    job.getId(),
                    job.getName(),
                    job.getProduct().getType().getDisplayName(),
                    job.getProduct().getGlaze().getDisplayName(),
                    String.valueOf(job.getQuantity()),
                    "UNASSIGNED",
                    String.valueOf(job.getDuration()))
            );
        }
        return jobRecordList;
    }

    @JsonPropertyOrder({"ID", "Job","ProductType", "Glaze", "Quantity", "Line", "Duration"})
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    record JobRecord (String id, String jobName, String type, String glaze, String quantity, String line, String duration) {

        @Override
        @JsonProperty("ID")
        public String id() {
            return id;
        }

        @Override
        @JsonProperty("Job")
        public String jobName() {
            return jobName;
        }

        @Override
        @JsonProperty("ProductType")
        public String type() {
            return type;
        }

        @Override
        @JsonProperty("Glaze")
        public String glaze() {
            return glaze;
        }
        @Override
        @JsonProperty("Quantity")
        public String quantity() {
            return quantity;
        }

        @Override
        @JsonProperty( "Line")
        public String line() {
            return line;
        }

        @Override
        @JsonProperty( "Duration")
        public String duration() {
            return duration;
        }
    }
}
