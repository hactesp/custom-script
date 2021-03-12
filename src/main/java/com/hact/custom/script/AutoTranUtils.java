package com.hact.custom.script;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoTranUtils {

    private BufferedWriter errorWriter;
    String errorFilename = "..\\error.txt";
    String sourceFolder = "..\\u3d_proj\\Config";
    String toTransFile = "..\\totran.txt";
    String transFile = "..\\trans.txt";
    String separator = "_,_";

    public AutoTranUtils() throws IOException {
        errorWriter = new BufferedWriter(new FileWriter(errorFilename));
    }

    public static void main(String[] args) throws IOException {


//        System.out.println("Start collect source file need trans");
        AutoTranUtils autoTranUtils = new AutoTranUtils();
        // collect lines
        autoTranUtils.listFilesUsingFileWalkAndVisitor();

        System.out.println("Done collect");

        // trans
        autoTranUtils.readContentFileAndCallAPI();

        System.out.println("Done trans");

        // write back to original files
        autoTranUtils.writeResultToFile();
        System.out.println("Done all");
    }

    public void listFilesUsingFileWalkAndVisitor() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(this.toTransFile));
        Files.walkFileTree(Paths.get(this.sourceFolder), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (Files.isDirectory(file) || !(file.getFileName().toString().endsWith("xml") || file.getFileName().toString().endsWith("lua")))
                    return FileVisitResult.CONTINUE;
                else {
                    visitFileAndCheckChineseLine(file, writer);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        writer.flush();
        writer.close();
    }

    public void visitFileAndCheckChineseLine(Path file, BufferedWriter writer) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file.toFile()));
        String line = null;
        int currentLineNr = 1;
        while ((line = reader.readLine()) != null) {
            String[] checkResult = checkIfStringContainsNonAscii(line);
            if (checkResult != null) {
                writer.append(file.toString())
                        .append(separator)
                        .append(String.valueOf(currentLineNr))
                        .append(separator)
                        .append(checkResult[0])
                        .append(separator)
                        .append(checkResult[1])
                        .append("\n");
            }
            // next line
            currentLineNr++;
        }
        writer.flush();
    }

    public String[] checkIfStringContainsNonAscii(String line) {
        int index = -1;
        boolean isStillWhitespace = true;
        boolean isASCII = true;
        for (int i = 0; i < line.length(); i++) {
            int c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                if (isStillWhitespace)
                    index = i;
            } else {
                isStillWhitespace = false;
            }
            if (c > 0x7F) {
//                System.out.println(fileContent.charAt(i));
                isASCII = false;
                break;
            }
        }
        if (!isASCII) {
            return new String[]{line.substring(0, index + 1), line.substring(index + 1)};
        }
        return null;
    }

    private void readContentFileAndCallAPI () throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(this.toTransFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(this.transFile));
        String[][] arr = new String[500][4];
        String line = null;

        boolean eofReached = false;
        while (!eofReached) {
            StringBuilder tranBatch = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                line = reader.readLine();
                if (line == null) {
                    eofReached = true;
                    break;
                }
                String[] components = line.split(separator);
                arr[i][0] = components[0];
                arr[i][1] = components[1];
                arr[i][2] = components[2];
                arr[i][3] = components[3];
                tranBatch.append(StringEscapeUtils.unescapeHtml4(components[3].trim())).append('\n');
            }
            // process batch
            String translated = trans(tranBatch.toString());
            appendToTransFile(arr, translated.split("\n"), writer);
        }

    }

    String trans(String input) throws IOException {
        URL url = new URL("http://vietphrase.info/Vietphrase/TranslateVietPhraseS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        String data = "{\"chineseContent\":\""+ StringEscapeUtils.escapeJson(input) +"\"}";
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(50000);
        connection.setReadTimeout(50000);
        connection.setRequestProperty("Content-Type", "application/json");
        DataOutputStream out = new DataOutputStream(connection.getOutputStream());
        out.write(data.getBytes());
        out.flush();
        out.close();
        int status = connection.getResponseCode();
        if (status == 200) {
            String responseBody = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            return responseBody;
        } else {
            this.errorWriter.append(input).append("\n");
            return input;
        }
    }

    void appendToTransFile(String[][] lines, String[] translated, BufferedWriter writer) throws IOException {

        for (int i = 0; i < translated.length && i < 500; i++) {
            writer.append(lines[i][0])
                    .append(separator)
                    .append(lines[i][1])
                    .append(separator)
                    .append(lines[i][2])
                    .append(separator)
                    .append(translated[i].trim())
                    .append('\n');
        }
        writer.flush();
    }

    public void writeResultToFile() throws IOException {
        BufferedReader transReader = new BufferedReader(new FileReader(transFile));
        String line = null;
        String prevFilename = "";
        List<String> prevFileContent = null;
        while ((line = transReader.readLine()) != null) {
            String[] split = line.split(separator);
            String currentFilename = split[0];
            List<String> fileContent = null;
            if (!currentFilename.equals(prevFilename)) {
                // save prev file
                if (StringUtils.isNotBlank(prevFilename)) {
                    Files.write(Paths.get(prevFilename), prevFileContent, StandardCharsets.UTF_8);
                }
                fileContent = new ArrayList<>(Files.readAllLines(Paths.get(currentFilename), StandardCharsets.UTF_8));
                prevFileContent = fileContent;
                prevFilename = currentFilename;
            }
            else {
                fileContent = prevFileContent;
            }
            fileContent.set(Integer.parseInt(split[1]) - 1, split[2] + split[3]);

        }
        Files.write(Paths.get(prevFilename), prevFileContent, StandardCharsets.UTF_8);
    }


}
