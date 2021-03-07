package com.hact.custom.script;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.text.StringEscapeUtils;

public class ReadFileInFolder {
    public Set<Path> listFilesUsingFileWalkAndVisitor(String dir) throws IOException {
        Set<Path> fileList = new HashSet<>();
        Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!Files.isDirectory(file)
                        && file.getFileName().toString().endsWith("xml")) {
                    fileList.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return fileList;
    }

    public static String callAPIVietPhase(String inputNeedToTrans, HttpURLConnection con, String contentSource) throws IOException {
        String input = "{\"chineseContent\":\""+ StringEscapeUtils.escapeHtml4(inputNeedToTrans) +"\"}";
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(5000);
        con.setReadTimeout(5000);
        con.setRequestProperty("Content-Type", "application/json");
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        out.write(input.getBytes());
        out.flush();
        out.close();
        int status = con.getResponseCode();
        StringBuilder content = new StringBuilder();
        if (status == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine).append(System.getProperty("line.separator"));
            }
            in.close();
            return StringEscapeUtils.unescapeHtml4(content.toString());
        } else {
            ReadFileInFolder.writeErrorFile(contentSource);
            return inputNeedToTrans;
        }
    }


    public static void main(String[] args) throws IOException {
        String sourceFolder = "C:\\WorkSpaces\\UET\\tmkd_client\\Config";
        String outPutDir = "C:\\Users\\hactE\\Desktop\\file-name.txt";
        String outPutDirTest = "C:\\Users\\hactE\\Desktop\\test-source-file.txt";

        collectPathOfContentFile(sourceFolder, outPutDir);

        try (Stream<String> stream = Files.lines(Paths.get(outPutDir))) {
            stream.forEach(contentSource -> {
                try {
                     List<String> resultLines = readContentFileAndCallAPI(contentSource);
                     writeResultToFile(resultLines, contentSource);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static String collectPathOfContentFile(String sourceFolder, String outPutDir) throws IOException {
        System.out.println("Start collect source file need trans");
        ReadFileInFolder readFileInFolder = new ReadFileInFolder();
        Set<Path> sets = readFileInFolder.listFilesUsingFileWalkAndVisitor(sourceFolder);
        new FileWriter(outPutDir, false).close();
        FileWriter fw = new FileWriter(outPutDir, true);
        BufferedWriter bw = new BufferedWriter(fw);
        sets.forEach(file -> {
            try {
                bw.write(file.toAbsolutePath().toString());
                bw.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        bw.close();
        System.out.println("Done, Number of file has been detected:" + sets.size());
        return outPutDir;
    }

    private static String writeResultToFile(List<String> resultLines, String outPutDir) throws IOException {
        new FileWriter(outPutDir, false).close();
        FileWriter fw = new FileWriter(outPutDir, true);
        BufferedWriter bw = new BufferedWriter(fw);
        resultLines.forEach(file -> {
            try {
                bw.write(file);
                bw.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        bw.close();
        return outPutDir;
    }

    private static void writeErrorFile(String sourceFile) {
        String outPutDirError = "C:\\Users\\hactE\\Desktop\\file-name-error.txt";
            try {
                FileWriter fw = new FileWriter(outPutDirError, true);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(sourceFile);
                bw.newLine();
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    private static List<String> readContentFileAndCallAPI (String contentSource) throws IOException {
        List<String> resultLines = new ArrayList<>();
                AtomicReference<URL> url = new AtomicReference<>();
                AtomicReference<HttpURLConnection> con = new AtomicReference<>();
                List<List<String>> listMatrix = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(contentSource))) {
            List<String> list = stream.collect(Collectors.toList());

            final List<String>[] listTemp = new List[]{new ArrayList<>()};
            list.forEach((sourceLineStream) -> {
                    if(listTemp[0].size() >500) {
                        listMatrix.add(listTemp[0]);
                        listTemp[0] = new ArrayList<>();
                    } else {
                        listTemp[0].add(sourceLineStream);
                    }
//                    if(!stream.iterator().hasNext() && listTemp[0].size() <500) {
                    if(list.get(list.size() -1) == sourceLineStream) {
                        listMatrix.add(listTemp[0]);
                        listTemp[0] = new ArrayList<>();
                    }
                });
            System.out.println("Number of minimal list: " + listMatrix.size());
        }
        AtomicReference<StringBuilder> sb = new AtomicReference<>(new StringBuilder());
        listMatrix.forEach(listTemp -> {
            listTemp.forEach(sourceStringItem -> {
                sb.get().append(sourceStringItem).append(System.getProperty("line.separator"));
            });
            try {
                System.out.println("Start call API");
                        url.set(new URL("http://vietphrase.info/Vietphrase/TranslateVietPhraseS"));
                        con.set((HttpURLConnection) url.get().openConnection());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        String resultLine = callAPIVietPhase(sb.get().toString(), con.get(),contentSource);
                        resultLines.add(resultLine);
                        con.get().disconnect();
                        sb.set(new StringBuilder());
                    } catch (IOException e) {
                        ReadFileInFolder.writeErrorFile(contentSource);
                        e.printStackTrace();
                        con.get().disconnect();
                        sb.set(new StringBuilder());
                    }
            System.out.println("Stop call API");

        });
        return resultLines;
    }
}
