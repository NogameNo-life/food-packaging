package org.acme.foodpackaging.bootstrap;

import org.acme.foodpackaging.domain.*;

import java.sql.*;
import java.time.*;
import java.util.*;

public class ImportOrderData {


    private static final int LINE_COUNT = 6;
    private static final int DEFAULT_PRIORITY = 0;
    private LocalDateTime DATE;
    private ProductNameShortener shortener;

    final int ALLERGEN_DIFFERENT_GLAZE = 90;
    final int CLEANING_AFTER_ALLERGEN = 240;
    final int CACTUS_CLEANING = 180;
    final int MIN_CLASSIC_GLAZE = 30;
    final int MAX_CLASSIC_GLAZE = 50;
    final int FROM_ROD_TO_CLASSIC = 150;
    final int ROD_DIFFERENT_FILLING = 50;
    final int DIFFERENT_CURD_MASS = 20;

    private static final Map<String, Boolean> IS_ALLERGEN = Map.of(
            "4810268043727", true,
            "4810268043475", true,
            "4810268054969", true,
            "4810268056826", true,
            "4810268054228", true,
            "4810268053870", true
    );

    public LocalDateTime getDATE() { return DATE; }
    public PackagingSchedule scheduleInitializer(String date){

        final LocalDate START_DATE = LocalDate.parse(date);
        final LocalDateTime START_DATE_TIME = LocalDateTime.of(START_DATE, LocalTime.of(8,0));
        final LocalDate END_DATE = START_DATE.plusDays(1);
        final LocalDateTime END_DATE_TIME = LocalDateTime.of(END_DATE, LocalTime.of(4,0));

        PackagingSchedule solution = new PackagingSchedule();
        this.shortener = new ProductNameShortener();

        solution.setWorkCalendar(new WorkCalendar(START_DATE, END_DATE));
        DATE = START_DATE_TIME;

        // Инициализация линий
        List<Line> lines = createLines(START_DATE_TIME);

        // Загрузка продуктов и заданий из БД
        Map<String, Product> productMap = new HashMap<>();
        List<Product> products = new ArrayList<>();
        List<Job> jobs = new ArrayList<>();

        String url = "jdbc:sqlserver://10.164.30.246;databaseName=MES;integratedSecurity=true;encrypt=true;trustServerCertificate=true";

        String sqlQuery = "SELECT v.KSK, v.SNPZ, v.DTI, v.DTM, v.KMC, v.EMK, v.KOLMV, v.MASSA, v.KOLEV, v.NP, v.UX, "
                + "m.MASSA, m.EAN13, m.SNM, m.NAME "
                + "FROM [MES].[dbo].[BD_VZPMC] as v, NS_MC as m "
                + "WHERE (v.KMC = m.KMC) AND (v.DTI = ?) AND (v.KSK = ?) AND (m.MASSA < ?) "
                + "ORDER BY v.SNPZ";

        try {
            // Установка соединения
            try (Connection connection = DriverManager.getConnection(url);
                 PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {

                // Установка параметров для SQL-запроса
                preparedStatement.setString(1, date + "T00:00:00");     // Параметр для v.DTI
                preparedStatement.setString(2, "0119030000");          // Параметр для v.KSK
                preparedStatement.setDouble(3, 0.1);                  // Параметр для m.MASSA

                int id=0;
                int duration;
                // Выполнение запроса
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    // Обработка результата

                    while (resultSet.next()) {
                        String snpz = resultSet.getString("SNPZ");
                        String ksk = resultSet.getString("KSK");
                        String dti = resultSet.getString("DTI");
                        String dtm = resultSet.getString("DTM");
                        String kmc = resultSet.getString("KMC");
                        String emk = resultSet.getString("EMK");
                        int kolmv = resultSet.getInt("KOLMV");
                        String vb = resultSet.getString("MASSA"); // MASSA из таблицы BD_VZPMC
                        int quantity= resultSet.getInt("KOLEV");
                        String np = resultSet.getString("NP");
                        String priority = resultSet.getString("UX");
                        double massaM = resultSet.getDouble("MASSA"); // MASSA из таблицы NS_MC
                        String ean13 = resultSet.getString("EAN13");
                        String snm = resultSet.getString("SNM");
                        String name = resultSet.getString("NAME");

                        Product product = productMap.get(ean13);
                        if (product == null) {
                            product = createProduct(ean13, name);
                            productMap.put(ean13, product);
                            products.add(product); // Добавляем только один раз
                        }

                        if(product.getType() == ProductType.PLUSH){
                            duration = quantity/164;
                        }
                        else if(product.getType() == ProductType.CACTUS){
                            duration = quantity/184;
                        }
                        else if(product.getType() == ProductType.ROD){
                            duration = quantity/198;
                        }
                        else{
                            duration = quantity/200;
                        }
                        // Создание задания
                        Job job = createJob(
                                String.valueOf(++id),
                                np,
                                product,
                                quantity,
                                duration,
                                DEFAULT_PRIORITY,
                                START_DATE_TIME
                        );
                        jobs.add(job);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println(e.getMessage()); // Выводим стек ошибок для отладки
        }

        // Инициализация времени очистки
        initCleaningDurations(products);

        solution.setLines(lines);
        solution.setProducts(products);
        jobs.sort(Comparator.comparing(Job::getName));
        solution.setJobs(jobs);

        return solution;
    }

    private Product createProduct(String id, String name) {
        ProductType type = determineProductType(name);
        return new Product(id, name, type, IS_ALLERGEN.getOrDefault(id, Boolean.FALSE));
    }

    private void initCleaningDurations(List<Product> products) {
        Random random = new Random();

        for (Product currentProduct : products) {
            Map<Product, Duration> cleaningDurationMap = new HashMap<>(products.size());

            for (Product previousProduct : products) {
                Duration cleaningDuration;


                // 1. Одинаковый продукт → без чистки
                if (currentProduct.getId().equals(previousProduct.getId())) {
                    cleaningDuration = Duration.ZERO;
                }
                // 2. Один из продуктов — CACTUS → всегда 3 часа
                else if (currentProduct.getType() == ProductType.CACTUS && previousProduct.getType() != ProductType.CACTUS
                        || currentProduct.getType() != ProductType.CACTUS && previousProduct.getType() == ProductType.CACTUS) {
                    cleaningDuration = Duration.ofMinutes(CACTUS_CLEANING);
                }
                // 3. Предыдущий аллерген, текущий — нет
                else if (previousProduct.is_allergen() && !currentProduct.is_allergen()) {
                    cleaningDuration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                }
                // 4. Текущий CLASSIC, предыдущий ROD
                else if (currentProduct.getType() == ProductType.CLASSIC
                        && previousProduct.getType() == ProductType.ROD) {
                    cleaningDuration = Duration.ofMinutes(FROM_ROD_TO_CLASSIC);
                }
                // 5. Оба ROD, разные глазури
                else if (currentProduct.getType() == ProductType.ROD
                        && previousProduct.getType() == ProductType.ROD
                        && !previousProduct.getGlaze().equals(GlazeType.C65_47)) {
                    cleaningDuration = Duration.ofMinutes(ROD_DIFFERENT_FILLING);
                }
                // 6. Оба аллергены, разные глазури
                else if (currentProduct.is_allergen() && previousProduct.is_allergen()
                        && currentProduct.getType() == ProductType.CLASSIC
                        && previousProduct.getType() == ProductType.CLASSIC
                        && !currentProduct.getGlaze().equals(previousProduct.getGlaze())) {
                    cleaningDuration = Duration.ofMinutes(ALLERGEN_DIFFERENT_GLAZE);
                }
                // 7. Текущий аллерген, предыдущий — нет
                else if (!currentProduct.is_allergen() && previousProduct.is_allergen()) {
                    cleaningDuration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                }
                // 8. Оба CLASSIC, разные глазури
                else if (currentProduct.getType() == ProductType.CLASSIC
                        && previousProduct.getType() == ProductType.CLASSIC
                        && !currentProduct.getGlaze().equals(previousProduct.getGlaze())) {
                    int minutes = MIN_CLASSIC_GLAZE + random.nextInt(MAX_CLASSIC_GLAZE - MIN_CLASSIC_GLAZE);
                    cleaningDuration = Duration.ofMinutes(minutes);
                }
                // 9. Одинаковый тип и глазурь, но разные ID
                else if (currentProduct.getType() == previousProduct.getType()
                        && currentProduct.getGlaze().equals(previousProduct.getGlaze())
                        && !currentProduct.getId().equals(previousProduct.getId())) {
                    cleaningDuration = Duration.ofMinutes(DIFFERENT_CURD_MASS);
                }
                // 10. По умолчанию
                else {
                    cleaningDuration = Duration.ofMinutes(MAX_CLASSIC_GLAZE);
                }

                cleaningDurationMap.put(previousProduct, cleaningDuration);
            }

            currentProduct.setCleaningDurations(cleaningDurationMap);
        }

    }

    private List<Line> createLines(LocalDateTime startDateTime){

        List<Line> lines = new ArrayList<>(ImportOrderData.LINE_COUNT);
        for(int i = 1; i<= ImportOrderData.LINE_COUNT; ++i){
            String lineName = "Line" + String.valueOf(i);
            String operatorName = "Operator" + String.valueOf(i);
            Line line = new Line(String.valueOf(i), lineName, operatorName,startDateTime);
            lines.add(line);
        }
        return lines;
    }
    private ProductType determineProductType(String productName) {
        String lowerName = productName.toLowerCase();

        if (containsAll(lowerName, "творобушки", "флоупак")) return ProductType.ROD;
        if (containsAll(lowerName, "топ", "флоупак")) return ProductType.ROD;
        if (containsAll(lowerName, "фольга")) return ProductType.PLUSH;
        if (containsAll(lowerName, "кактус")) return ProductType.CACTUS;
        return ProductType.CLASSIC;
    }

    private boolean containsAll(String text, String... keywords) {
        for (String kw : keywords) {
            if (!text.contains(kw)) return false;
        }
        return true;
    }

    private Job createJob(String id, String np, Product product, int quantity, int duration, int priority, LocalDateTime startDate) {
        String jobName = shortener.getShortName(product.getId(), product.getName());
        return new Job(
                id,
                jobName,
                np,
                product,
                quantity,
                Duration.ofMinutes(duration),
                startDate,
                startDate.plusDays(1).withHour(2).withMinute(0), // Идеальное время завершения
                startDate.plusDays(1).withHour(4).withMinute(0), // Максимальное время завершения
                priority,
                false
        );

    }
}
