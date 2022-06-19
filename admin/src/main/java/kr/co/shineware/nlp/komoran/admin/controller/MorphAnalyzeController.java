package kr.co.shineware.nlp.komoran.admin.controller;

import kr.co.shineware.nlp.komoran.admin.service.FileUploadService;
import kr.co.shineware.nlp.komoran.admin.service.MorphAnalyzeService;
import kr.co.shineware.nlp.komoran.admin.util.ModelValidator;
import kr.co.shineware.nlp.komoran.admin.util.ResponseDetail;
import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.Token;
import kr.co.shineware.util.common.file.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/analyze")
public class MorphAnalyzeController {

    private static final Logger logger = LoggerFactory.getLogger(DicUserController.class);

    @Autowired
    private MorphAnalyzeService morphAnalyzeService;

    @Autowired
    private FileUploadService fileUploadService;

    @Value("${user.environment}")
    String environment;

    @Value("${user.prodpath}")
    String prodpath;

    @Value("${heroku.connurl}")
    String connurl;

    @Value("${heroku.user}")
    String user;

    @Value("${heroku.password}")
    String password;

    @GetMapping("/tt01")
    public Object doMining(){
        logger.info(environment);
        return environment;
    }

    @GetMapping("/batch-run")
    public ResponseEntity batchRun(HttpServletRequest req){
        File dir = new File(this.prodpath);

//        List<String> tableList = new ArrayList();
//        tableList.add("IT_science,csv,news_itscience");
//        tableList.add("economy,csv,news_economy");
//        tableList.add("living_culture,csv,news_living");
//        tableList.add("opinion,csv,news_opinion");
//        tableList.add("politics,csv,news_politics");
//        tableList.add("society,csv,news_society");
//        tableList.add("world,csv,news_world");

        String[] filenames = dir.list();

        for (String filename : filenames) {

            logger.info("filename : {}", filename);

            if(!filename.endsWith(".csv")){
                logger.info("is not csv file, skipped");
                continue;
            }

            String tblName = "";

            if(filename.startsWith("Article_opinion")){
                tblName = "news_opinion";
            }
            else if(filename.startsWith("Article_IT_science")){
                tblName = "news_itscience";
            }
            else if(filename.startsWith("Article_living_culture")){
                tblName = "news_living";
            }
            else if(filename.startsWith("Article_politics")){
                tblName = "news_politics";
            }
            else if(filename.startsWith("Article_economy")){
                tblName = "news_economy";
            }
            else if(filename.startsWith("Article_society")){
                tblName = "news_society";
            }
            else if(filename.startsWith("Article_world")){
                tblName = "news_world";
            }else{
                logger.info("skip file : {}", filename);
                continue;
            }

            try {
                logger.info("start do mining {}, {}", filename, tblName);
                doMining(filename, tblName);
            } catch (Exception e) {
                logger.info("method batchRun Exception : {}", e.getCause());
                e.printStackTrace();
            }

        }

        return ResponseEntity.ok().body(1);
    }

    @GetMapping("/readfiles")
    public ResponseEntity readFiles(){
        String DATA_DIRECTORY = prodpath;
        File dir = new File(DATA_DIRECTORY);

        String[] filenames = dir.list();
        for (String filename : filenames) {
            System.out.println("filename : " + filename);
        }
//        postgres://ynfvpocqhghxmn:1ca560b69047b19dfae3cf9f22d4481280a1084e922caebc8a010e837c84f876@ec2-52-49-120-150.eu-west-1.compute.amazonaws.com:5432/d7lgjb2l3i4u6b

        return null;
    }

    @GetMapping("/delfiles")
    public ResponseEntity delFiles(){
        return null;
    }

    @GetMapping("/do-mining/{fileName}/{tblName}")
    public ResponseEntity doMining(@PathVariable String fileName, @PathVariable String tblName) {

        List<Map> returnList = new ArrayList<>();
        Komoran komoran = new Komoran(DEFAULT_MODEL.STABLE);
        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(prodpath + fileName), "UTF-8"));

            while ((line = br.readLine()) != null) { // readLine()은 파일에서 개행된 한 줄의 데이터를 읽어온다.
                String[] lineArr = line.split(","); // 파일의 한 줄을 ,로 나누어 배열에 저장 후 리스트로 변환한다.
                List<String> aLine = Arrays.asList(lineArr);

                if(aLine.size() < 5){
                    logger.info("uncompleted line : {}", line);
                    continue;
                }

                KomoranResult analyzeResultList = komoran.analyze(aLine.get(4));

                // Grouping by based on the count
                Map<String, Long> countMap = analyzeResultList
                        .getNouns().stream()
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                Map<String, Long> groupBySorted = countMap.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2, LinkedHashMap::new));

                List<String> rtnList = new ArrayList<>();

                int i=0;
                for (String s : groupBySorted.keySet()) {
                    if(i > 10){
                        break;
                    }
                    rtnList.add(s);
                    i++;
                }

                Map returnMap = new HashMap();
                returnMap.put("weight", rtnList);
                returnMap.put("content", aLine);
                returnList.add(returnMap);
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close(); // 사용 후 BufferedReader를 닫아준다.
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        batchJob(returnList, tblName);

        return ResponseEntity.ok().body(returnList);
    }

    private int batchJob(List<Map> returnList, String tblName) {

        String currDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
//        postgres://ynfvpocqhghxmn:1ca560b69047b19dfae3cf9f22d4481280a1084e922caebc8a010e837c84f876@ec2-52-49-120-150.eu-west-1.compute.amazonaws.com:5432/d7lgjb2l3i4u6b
//        String     connurl  = "jdbc:postgresql://ec2-52-49-120-150.eu-west-1.compute.amazonaws.com:5432/d7lgjb2l3i4u6b";
//        String     user     = "ynfvpocqhghxmn";
//        String     password = "1ca560b69047b19dfae3cf9f22d4481280a1084e922caebc8a010e837c84f876";
        String     connurl  = this.connurl;
        String     user     = this.user;
        String     password = this.password;

        try (Connection connection = DriverManager.getConnection(connurl, user, password);) {

            PreparedStatement pstmt = null;

            String SQL = "" +
                    "INSERT INTO public." + tblName + "\n" +
                    "(sys_occ_dt, sys_occ_ip, writer, read_date, read_title, read_content, read_url, read_gbn, gbn_cd, weight01, weight02, weight03, weight04, weight05, weight06, weight07, weight08, weight09, weight10)\n" +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);" +
                    "";

            pstmt = connection.prepareStatement(SQL);

            for (Map item : returnList) {
                List<String> weightLst = (List) item.get("weight");
                List<String> contentLst = (List) item.get("content");

                int wIdx = 10;
                for (String str : weightLst) {
                    if(wIdx == 20) break;

                    if(str.equals("") || str == null){
                        pstmt.setString(wIdx, "");
                    }else{
                        pstmt.setString(wIdx, str);
                    }
                    wIdx++;
                }

                pstmt.setString(1, currDate);
                pstmt.setString(2, "");
                pstmt.setString(3, contentLst.get(2));
                pstmt.setString(4, contentLst.get(0));
                pstmt.setString(5, contentLst.get(3));
                pstmt.setString(6, contentLst.get(4));
                pstmt.setString(7, contentLst.get(5));
                pstmt.setString(8, contentLst.get(1));
                pstmt.setString(9, "");

                // 5. SQL 문장을 실행하고 결과를 리턴 - SQL 문장 실행 후, 변경된 row 수 int type 리턴
                pstmt.executeUpdate();
            }
            pstmt.close();
            connection.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }finally {
        }
        return 1;
    }


    @GetMapping("/mining-set/{input}")
    public Object miningSet(@PathVariable String input) throws Exception {

        Komoran komoran = new Komoran(DEFAULT_MODEL.STABLE);
        komoran.setFWDic("user_data/fwd.user");
        komoran.setUserDic("user_data/dic.user");

//        String input = "밀리언 달러 베이비랑 바람과 함께 사라지다랑 뭐가 더 재밌었어?";
        KomoranResult analyzeResultList = komoran.analyze(input);
        List<Token> tokenList = analyzeResultList.getTokenList();

        // 1. print each tokens by getTokenList()
        System.out.println("==========print 'getTokenList()'==========");
        for (Token token : tokenList) {
            System.out.println(token);
            System.out.println(token.getMorph() + "/" + token.getPos() + "(" + token.getBeginIndex() + "," + token.getEndIndex() + ")");
            System.out.println();
        }

        // 2. print nouns
        System.out.println("==========print 'getNouns()'==========");
        System.out.println(analyzeResultList.getNouns());
        System.out.println();

        // 3. print analyzed result as pos-tagged text
        System.out.println("==========print 'getPlainText()'==========");
        System.out.println(analyzeResultList.getPlainText());
        System.out.println();

        // 4. print analyzed result as list
        System.out.println("==========print 'getList()'==========");
        System.out.println(analyzeResultList.getList());
        System.out.println();

        // 5. print morphes with selected pos
        System.out.println("==========print 'getMorphesByTags()'==========");
        System.out.println(analyzeResultList.getMorphesByTags("NP", "NNP", "JKB"));

//        List<KomoranResult> analyzeList = komoran.analyze(input, 10);

        return analyzeResultList;
    }

    @PostMapping(value = "/default")
    public ResponseDetail analyzeStr(@RequestParam("strToAnalyze") String strToAnalyze,
                                     @RequestParam("modelName") String modelNameToUse) {
        ModelValidator.CheckValidModelName(modelNameToUse);

        ResponseDetail responseDetail = new ResponseDetail();

        String analyzedResult = morphAnalyzeService.analyzeWithUserModel(strToAnalyze, modelNameToUse);

        responseDetail.setData(analyzedResult);

        return responseDetail;
    }


    @PostMapping(value = "/multiple")
    public ResponseDetail analyzeMultipleStrs(@RequestParam("strToAnalyze") String strToAnalyze,
                                              @RequestParam("modelName") String modelNameToUse) {
        ModelValidator.CheckValidModelName(modelNameToUse);

        ResponseDetail responseDetail = new ResponseDetail();

        ArrayList<String> analyzedResults = morphAnalyzeService.analyzeMultipleLinesWithUserModel(strToAnalyze, modelNameToUse);
        String result = String.join("\n", analyzedResults);

        responseDetail.setData(result);

        return responseDetail;
    }


    @PostMapping(value = "/compare")
    public ResponseDetail analyzeStrWithNewModel(@RequestParam("strToAnalyze") String strToAnalyze,
                                                 @RequestParam("modelNameSrc") String modelNameSrc,
                                                 @RequestParam("modelNameDest") String modelNameDest) {
        ModelValidator.CheckValidModelName(modelNameSrc);
        ModelValidator.CheckValidModelName(modelNameDest);

        ResponseDetail responseDetail = new ResponseDetail();

        Map<String, String> result = morphAnalyzeService.getDiffsFromAnalyzedResults(strToAnalyze, modelNameSrc, modelNameDest);
        responseDetail.setData(result);

        return responseDetail;
    }


    @PostMapping(value = "/multicompare")
    public ResponseDetail analyzeMultipleStrsWithNewModel(@RequestParam("strToAnalyze") String strToAnalyze,
                                                          @RequestParam("modelNameSrc") String modelNameSrc,
                                                          @RequestParam("modelNameDest") String modelNameDest) {
        ModelValidator.CheckValidModelName(modelNameSrc);
        ModelValidator.CheckValidModelName(modelNameDest);

        ResponseDetail responseDetail = new ResponseDetail();

        Map<String, String> result = morphAnalyzeService.getDiffsFromAnalyzedMultipleResultsForHtml(strToAnalyze, modelNameSrc, modelNameDest);
        responseDetail.setData(result);

        return responseDetail;
    }

    @PostMapping(value = "/diff/file")
    public ResponseDetail uploadFile(@RequestParam("modelNameSrc") String modelNameSrc,
                                     @RequestParam("modelNameDest") String modelNameDest,
                                     @RequestParam("file") MultipartFile fileToAnalyze) {

        ModelValidator.CheckValidModelName(modelNameSrc);
        ModelValidator.CheckValidModelName(modelNameDest);

        List<String> diffResultList = morphAnalyzeService.getDiffsFromFiles(fileToAnalyze, modelNameSrc, modelNameDest);
        List<String> toShowDiffRows = diffResultList.subList(0, Math.min(50, diffResultList.size()));
        Map<String, String> result = morphAnalyzeService.generateDiffRows(toShowDiffRows);

        if (diffResultList.size() != 0) {
            FileUtil.makePath("diff_logs");
            String filename = System.currentTimeMillis() + ".txt";
            FileUtil.writeList(diffResultList, "diff_logs/" + filename);
            result.put("filepath", filename);
        }
        ResponseDetail responseDetail = new ResponseDetail();
        responseDetail.setData(result);

        return responseDetail;
    }

    @GetMapping(value = "/diff/file/download")
    public ResponseEntity<ByteArrayResource> downloadFile(@RequestParam(value = "filename") String filename) {
        List<String> lines = FileUtil.load2List("diff_logs/" + filename);
        logger.info("Loading " + filename);

        ByteArrayResource resource = diffResultListToByteArrayResource(lines);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(resource);
    }

    private ByteArrayResource diffResultListToByteArrayResource(List<String> diffResultList) {
        return new ByteArrayResource(String.join("\n", diffResultList).getBytes());
    }

//    public static void main(String[] args) {
//        CSVReader csvReader = new CSVReader();
//        csvReader.readCSV();
//    }

    public static List<List<String>> readCSV() {
        List<List<String>> csvList = new ArrayList<List<String>>();
        File csv = new File("여기에 .csv파일의 절대경로를 입력한다");
        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(csv));
            while ((line = br.readLine()) != null) { // readLine()은 파일에서 개행된 한 줄의 데이터를 읽어온다.
                List<String> aLine = new ArrayList<String>();
                String[] lineArr = line.split(","); // 파일의 한 줄을 ,로 나누어 배열에 저장 후 리스트로 변환한다.
                aLine = Arrays.asList(lineArr);
                csvList.add(aLine);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close(); // 사용 후 BufferedReader를 닫아준다.
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        return csvList;
    }


    public static void main(String[] args){
        System.out.println(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

    }
}
