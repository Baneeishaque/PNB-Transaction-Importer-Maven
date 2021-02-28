/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package account.ledger.pnb_transaction_importer;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Admin
 */
public class Main {

    int i;

    // one instance, reuse
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        Main main = new Main();

        String csvFile = "input_files/4356XXXXXXXXX854510-03-2020.csv";
        Integer[] excludeList = new Integer[]{18, 25};
        int startLineNumber = 18;
        int endLineNumber = 26;
        int userId = 15;
        int unProcessedCreditsId = 684;
        int pnbAccountId = 11;
        int unProcessedDebitsId = 683;
        String serverApiUrl = "http://account-ledger-server.herokuapp.com/http_API/insert_Transaction_v2.php";

        main.readCsv(csvFile, excludeList, startLineNumber, endLineNumber, userId, unProcessedCreditsId, pnbAccountId, unProcessedDebitsId, serverApiUrl);
    }

    void readCsv(String csvFile, Integer[] excludeList, int startingLineNumber, int endLineNumber, int userId, int unProcessedCreditsId, int pnbAccountId, int unProcessedDebitsId, String serverApiUrl) {

        CSVReader reader;

        try {

            reader = new CSVReader(new FileReader(getClass().getClassLoader().getResource(csvFile).getFile()));

            String[] line;

            i = 1;

            String previousTransactionDate = "";

            String pnbTransactionDateTimeFormat = "dd/MM/yyyy HH:mm:ss";
            DateTimeFormatter pnbTransactionDateFormatter = DateTimeFormatter.ofPattern(pnbTransactionDateTimeFormat);

            String mySqlDateTimeFormat = "yyyy-MM-dd HH:mm:ss";
            DateTimeFormatter mySqlDateTimeFormatter = DateTimeFormatter.ofPattern(mySqlDateTimeFormat);

            String railwayTimeFormat = "HH:mm:ss";
            DateTimeFormatter railwayTimeFormatter = DateTimeFormatter.ofPattern(railwayTimeFormat);

            LocalTime initialTime = LocalTime.parse("09:00:00", railwayTimeFormatter);

            LocalTime nextTime = LocalTime.now();

            while ((line = reader.readNext()) != null) {

//                System.out.println(i+" "+!Arrays.asList(excludeList).contains(i));
                if (i >= startingLineNumber && i <= endLineNumber && (!Arrays.asList(excludeList).contains(i))) {

//                    Arrays.stream(line).forEach(System.out::println);
                    System.out.println("Transaction Date= " + line[1] + ", Withdrawal=" + (line[5].isEmpty() ? "0" : line[5]) + ", Deposit=" + (line[7].isEmpty() ? "0" : line[7]) + ", Particulars=" + line[9]);

                    LocalDateTime currentTransactionDateTime;

                    if (previousTransactionDate.equals(line[1])) {

                        currentTransactionDateTime = LocalDateTime.parse(line[1] + " " + nextTime.toString() + ":00", pnbTransactionDateFormatter);
                        nextTime = nextTime.plusMinutes(5);

                    } else {

                        currentTransactionDateTime = LocalDateTime.parse(line[1] + " " + initialTime.toString() + ":00", pnbTransactionDateFormatter);
                        previousTransactionDate = line[1];
                        nextTime = initialTime.plusMinutes(5);
                    }

                    //Withdraw is 0 - means deposit
                    if (line[5].isBlank()) {

                        performHttpPost(currentTransactionDateTime.format(mySqlDateTimeFormatter), userId, line[9], line[7], unProcessedCreditsId, pnbAccountId, serverApiUrl);

                    } else {

                        performHttpPost(currentTransactionDateTime.format(mySqlDateTimeFormatter), userId, line[9], line[5], pnbAccountId, unProcessedDebitsId, serverApiUrl);
                    }
                }
                i++;
            }
        }
        catch (IOException | CsvValidationException | DateTimeParseException | InterruptedException e) {

            System.out.println("Error : " + e.getLocalizedMessage());
        }
    }

    void performHttpPost(String eventDateTime, int userId, String particulars, String amount, int fromAccountId, int toAccountId, String serverApiUrl) throws IOException, InterruptedException {

//        $event_date_time = filter_input(INPUT_POST, 'event_date_time');
//        $user_id = filter_input(INPUT_POST, 'user_id');
//        $particulars = filter_input(INPUT_POST, 'particulars');
//        $amount = filter_input(INPUT_POST, 'amount');
//        $from_account_id = filter_input(INPUT_POST, 'from_account_id');
//        $to_account_id = filter_input(INPUT_POST, 'to_account_id');

        // form parameters
        Map<Object, Object> data = new HashMap<>();
        data.put("event_date_time", eventDateTime);
        data.put("user_id", userId);
        data.put("particulars", particulars);
        data.put("amount", amount);
        data.put("from_account_id", fromAccountId);
        data.put("to_account_id", toAccountId);

        HttpRequest request = HttpRequest.newBuilder()
                .POST(buildFormDataFromMap(data))
                .uri(URI.create(serverApiUrl))
                .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // print status code
//        System.out.println(response.statusCode());

        // print response body
        System.out.println(response.body());
    }

    private static HttpRequest.BodyPublisher buildFormDataFromMap(Map<Object, Object> data) {

        var builder = new StringBuilder();
        data.entrySet().stream().map((entry) -> {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            return entry;
        }).forEachOrdered((entry) -> {
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        });
        System.out.println(builder.toString());
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }
}
