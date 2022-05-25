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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
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

    @GetMapping("/do-mining")
    public Object doMining(){


        List<Map> returnList = new ArrayList<>();

        Komoran komoran = new Komoran(DEFAULT_MODEL.STABLE);
//        komoran.setFWDic("user_data/fwd.user");
//        komoran.setUserDic("user_data/dic.user");

        List<List<String>> csvList = new ArrayList<List<String>>();
        List<Map> rtn = new ArrayList<Map>();
//        File csv = new File("C://Users//82109//Documents//GitHub//0509//KoreaNewsCrawler//output/Article_IT_science_20220523_20220523.csv");

        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream("C://Users//82109//Documents//GitHub//0509//KoreaNewsCrawler//output/Article_IT_science_20220523_20220523.csv"), "EUC-KR"));

            while ((line = br.readLine()) != null) { // readLine()은 파일에서 개행된 한 줄의 데이터를 읽어온다.
                List<String> aLine = new ArrayList<String>();
                String[] lineArr = line.split(","); // 파일의 한 줄을 ,로 나누어 배열에 저장 후 리스트로 변환한다.
                aLine = Arrays.asList(lineArr);

                KomoranResult analyzeResultList = komoran.analyze(aLine.get(4));

                // Grouping by based on the count
                Map<String, Long> countMap = analyzeResultList.getNouns().stream()
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                Map<String, Long> groupBySorted = countMap.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2, LinkedHashMap::new));

                List<String> rtnList = new ArrayList<>();

                int i=0;
                for (String s : groupBySorted.keySet()) {
                    if(i > 4){
                        break;
                    }
                    rtnList.add(s);
                    i++;
                }

//                if(rtnList.size() > 0){
//                    csvList.add(rtnList);
//                }
//                csvList.add(aLine);
//                Map mappp = new HashMap();
//                mappp.put("aLine",aLine);
//                mappp.put("csvList",csvList);
//                rtn.add(mappp);

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

        return returnList;

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

}
