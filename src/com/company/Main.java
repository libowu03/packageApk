package com.company;

import brut.androlib.ApktoolProperties;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.Buffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brut.androlib.Androlib;
import brut.androlib.ApkDecoder;
import brut.androlib.ApkOptions;

public class Main {
    //app名称
    private static String appName = "你好呀";
    //app图标位置
    private static String iconPath = "";
    //使用线程池进行图片读写
    private static ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //配置列表
    private static List<String[]> configList = new ArrayList<>();
    //AndroidManifest文件的所有文本内容，包含了每一行内容。key为线程名称
    private static HashMap<String,List<String>> androidManifileMap = new HashMap();
    //csv第几列对应的标题
    private static HashMap<Integer, String> configTitleMap = new HashMap<>();
    //签名配置文件的标题
    private static HashMap<Integer, String> keyConfigTitleMap = new HashMap<>();
    //签名配置文件的具体内容
    private static HashMap<String, String> keyConfigMap = new HashMap<>();
    //反编译后临时数据存放地点
    private static String outPath = "./tmp/";
    //目标apk存放路径
    private static String inputPath = "./apk";
    //打包进度
    private volatile static int progress = -1;
    //是否需要复用上次反编译的文件
    private static boolean isNeedPreviousFile = false;
    private static boolean onlySignApk = false;
    //开始时间
    private static long startTime;
    //最大线程数量
    private static int maxThreadNum = 5;
    //已经完成的签名数量，这个签名完成数量是真正的完成数量，而progress只是执行数量，因为签名是另外新开线程执行的，所以progress与打包数量相同时不一定完成，而签名数量与打包数量相同时，则一定完成了所有任务
    private volatile static int signNum = 0;


    public static void main(String[] args) {
        for (String item:args){
            if (item.equals("-n")){
                isNeedPreviousFile = true;
            }else if (item.equals("-s")){
                onlySignApk = true;
            }
        }
        startTime = System.currentTimeMillis();

        System.out.println("必要文件检测......");
        checkFiles();
        writeExampleConfig();
        System.out.println("必要文件检测完成");

        System.out.println("读取配置文件中......");
        readConfigFile();
        System.out.println("读取配置文件完成！");

        //检测签名配置文件是否正常
        if (!checkKeyConfig()) {
            System.exit(0);
            return;
        }

        if (onlySignApk){
            File file = readUserInputFile();
            if (file.getName().contains(" ")){
                System.out.println("文件名称不允许存在空格符，已将文件名由"+file.getName()+"改为"+file.getName().replaceAll(" ",""));
                file.renameTo(new File("./apk/"+file.getName().replaceAll(" ","")));
            }
            Utils.signApk("./apk/"+file.getName().replaceAll(" ",""),"./resultApk/"+file.getName().replaceAll(" ",""),keyConfigMap.get("key"),keyConfigMap.get("password"),keyConfigMap.get("alias"));
            System.out.println("");
            System.out.println("==================签名完成！==================");
            System.exit(0);
        }else {
            //开启多条线程进行打包。线程数量为打包数量的1/3，超过最大线程则使用最大线程数量
            int threadNum = configList.size()/3;
            if (configList.size() < 5){
                threadNum = 1;
            }else {
                if (threadNum > maxThreadNum){
                    threadNum = maxThreadNum;
                }
            }
            progress = threadNum-1;
            for (int i=0;i < threadNum;i++){
                addMission(i);
            }
        }
    }

    private static void addMission(int threadNum){
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isNeedPreviousFile){
                        System.out.println("执行反编译工作.........");
                        File out = new File(outPath+Thread.currentThread().getName());
                        File inputFile = readUserInputFile();
                        if (inputFile == null) {
                            return;
                        }
                        //System.out.println("获取的目录为："+inputFile.getCanonicalPath());
                        ApkDecoder decoder = new ApkDecoder();
                        decoder.setOutDir(out);
                        decoder.setForceDelete(true);
                        decoder.setApkFile(inputFile);
                        decoder.decode();
                        System.out.println("反编译完成！");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("反编译失败！程序结束。" + e.getMessage());
                    System.exit(0);
                    return;
                }
                readAndroidManifile();
                copySourceIcon();
                makeNewAndroidManifest(threadNum);
            }
        });
    }

    /**
     * 写入示例配置文件
     */
    private static void writeExampleConfig() {

        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    String keyConfig = "key,password,alias,default\n" +
                            "密钥文件名,密钥密码,密钥别名,是否是默认密钥（TRUE为默认密钥）\n";
                    String channelConfig = "appName,UMENG_CHANNEL,alc_channel,appIcon,outName\n" +
                            "app名称1,自定义参数,自定义参数,app图标,输出app名称\n" +
                            "app名称2,自定义参数2,自定义参数2,,输出app名称2";

                    File exampleConfig = new File("./exampleConfig").getCanonicalFile();
                    exampleConfig.mkdirs();
                    BufferedWriter keyConfigWriter = new BufferedWriter(new FileWriter((new File("./exampleConfig/keyConfig.csv")).getCanonicalFile()));
                    keyConfigWriter.write(keyConfig);
                    keyConfigWriter.flush();
                    keyConfigWriter.close();

                    BufferedWriter channelConfigWriter = new BufferedWriter(new FileWriter((new File("./exampleConfig/channelConfig.csv")).getCanonicalFile()));
                    channelConfigWriter.write(channelConfig);
                    channelConfigWriter.flush();
                    channelConfigWriter.close();
                }catch (Exception e){

                }
            }
        });
    }

    /**
     * 将原包中的图标文件拷贝一份到./ico/source下面去
     */
    private synchronized static void copySourceIcon() {
        try {
            File resList = new File("./tmp/"+Thread.currentThread().getName()+"/res");
            if (resList != null && resList.listFiles() != null) {
                //进行icon的图片替换工作。这里对icon路径进行分析，判断icon在drawable目录还是mipmap目录,及获取icon文件的名称
                final String[] icon = iconPath.split("/");
                for (File file : resList.listFiles()) {
                    if (file.getName().startsWith(icon[0]) && !file.isFile()) {
                        File[] iconList = file.listFiles();
                        if (file.getName().equals(icon[0] + "-xhdpi")) {
                            for (File iconFile : iconList) {
                                String[] iconName = iconFile.getName().split("\\.");
                                if (iconName[0].equals(icon[1])) {
                                    //System.out.println("进入此处xhdpi");
                                    copyIcon(icon[0], iconFile.getName(), "xhdpi");
                                }
                            }
                        } else if (file.getName().equals(icon[0] + "-hdpi")) {
                            for (File iconFile : iconList) {
                                String[] iconName = iconFile.getName().split("\\.");
                                if (iconName[0].equals(icon[1])) {
                                    //System.out.println("进入此处xhdpi");
                                    copyIcon(icon[0], iconFile.getName(), "hdpi");
                                }
                            }
                        } else if (file.getName().equals(icon[0] + "-xxhdpi")) {
                            for (File iconFile : iconList) {
                                String[] iconName = iconFile.getName().split("\\.");
                                if (iconName[0].equals(icon[1])) {
                                    //System.out.println("进入此处xhdpi");
                                    copyIcon(icon[0], iconFile.getName(), "xxhdpi");
                                }
                            }
                        } else if (file.getName().equals(icon[0] + "-xxxhdpi")) {
                            for (File iconFile : iconList) {
                                String[] iconName = iconFile.getName().split("\\.");
                                if (iconName[0].equals(icon[1])) {
                                    //System.out.println("进入此处xxxhdpi");
                                    copyIcon(icon[0], iconFile.getName(), "xxxhdpi");
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始拷贝原始图片到icon/source下
     *
     * @param iconType 有drawable和mipmap两种
     * @param iconName 图片名称
     * @param folder   存在xhdpi,xxhdpi,xxxhdpi几种
     */
    private static void copyIcon(String iconType, String iconName, String folder) {
        try {
            FileInputStream fils = new FileInputStream((new File("./tmp/"+Thread.currentThread().getName()+"/res/" + (iconType + "-" + folder + "") + "/" + iconName)).getCanonicalFile());
            FileOutputStream outs = new FileOutputStream((new File("./icon/source/" + folder + "/" + iconName)).getCanonicalFile());
            // 读取和写入信息
            int len = 0;
            while ((len = fils.read()) != -1) {
                outs.write(len);
            }
            outs.close();
            fils.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查签名配置文件是否正常
     */
    private static boolean checkKeyConfig() {
        try {
            if (keyConfigMap.size() == 0) {
                System.out.println("./key/keyConfig.csv文件是否完善，是否在default中标记了true默认签名");
                return false;
            }
            if (keyConfigMap.get("key") == null || keyConfigMap.get("key").isEmpty()) {
                System.out.println("./key/keyConfig.csv文件中不存在key数据,请确认签名文件是否配置完善");
                return false;
            }
            if (keyConfigMap.get("password") == null || keyConfigMap.get("password").isEmpty()) {
                System.out.println("./key/keyConfig.csv文件中不存在password数据,请确认签名文件是否配置完善");
                return false;
            }
            if (keyConfigMap.get("alias") == null || keyConfigMap.get("alias").isEmpty()) {
                System.out.println("./key/keyConfig.csv文件中不存在alias数据,请确认签名文件是否配置完善");
                return false;
            }
            File file = new File("./key/" + keyConfigMap.get("key")).getCanonicalFile();
            if (file.exists() && file.isFile()) {
                return true;
            } else {
                System.out.println("./key文件夹中不存在" + keyConfigMap.get("key") + "文件");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * 检测apk，config，resultApk,tmp,key目录是否存在
     */
    private static void checkFiles() {
        try {
            File apk = (new File("./apk")).getCanonicalFile();
            File key = (new File("./key")).getCanonicalFile();
            File config = (new File("./config")).getCanonicalFile();
            File resultApk = (new File("./resultApk")).getCanonicalFile();
            File tmp = (new File("./tmp")).getCanonicalFile();
            File icon = (new File("./icon/hdpi")).getCanonicalFile();
            File iconX = (new File("./icon/xhdpi")).getCanonicalFile();
            File iconXX = (new File("./icon/xxhdpi")).getCanonicalFile();
            File iconXXX = (new File("./icon/xxxhdpi")).getCanonicalFile();
            File sourceIcon = (new File("./icon/source/hdpi")).getCanonicalFile();
            File sourceIconX = (new File("./icon/source/xhdpi")).getCanonicalFile();
            File sourceIconXX = (new File("./icon/source/xxhdpi")).getCanonicalFile();
            File sourceIconXXX = (new File("./icon/source/xxxhdpi")).getCanonicalFile();
            if (!apk.exists()) {
                apk.mkdir();
            } else if (apk.isFile()) {
                apk.mkdir();
            }
            if (!config.exists()) {
                config.mkdir();
            } else if (config.isFile()) {
                config.mkdir();
            }
            if (!resultApk.exists()) {
                resultApk.mkdir();
            } else if (resultApk.isFile()) {
                resultApk.mkdir();
            }
            if (!tmp.exists()) {
                tmp.mkdir();
            } else if (tmp.isFile()) {
                tmp.mkdir();
            }
            if (!icon.exists()) {
                icon.mkdirs();
            } else if (icon.isFile()) {
                icon.mkdirs();
            }
            if (!iconX.exists()) {
                iconX.mkdirs();
            } else if (iconX.isFile()) {
                iconX.mkdirs();
            }
            if (!iconXX.exists()) {
                iconXX.mkdirs();
            } else if (iconXX.isFile()) {
                iconXX.mkdirs();
            }
            if (!iconXXX.exists()) {
                iconXXX.mkdirs();
            } else if (iconXXX.isFile()) {
                iconXXX.mkdirs();
            }
            if (!sourceIcon.exists()) {
                sourceIcon.mkdirs();
            } else if (sourceIcon.isFile()) {
                sourceIcon.mkdirs();
            }
            if (!sourceIconX.exists()) {
                sourceIconX.mkdirs();
            } else if (sourceIconX.isFile()) {
                sourceIconX.mkdirs();
            }
            if (!sourceIconXX.exists()) {
                sourceIconXX.mkdirs();
            } else if (sourceIconXX.isFile()) {
                sourceIconXX.mkdirs();
            }
            if (!sourceIconXXX.exists()) {
                sourceIconXXX.mkdirs();
            } else if (sourceIconXXX.isFile()) {
                sourceIconXXX.mkdirs();
            }
            if (!key.exists()) {
                key.mkdir();
            } else if (key.isFile()) {
                key.mkdir();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File readUserInputFile() {
        File inFile = (new File(inputPath)).getAbsoluteFile();
        if (inFile == null) {
            inFile.mkdir();
            return null;
        } else if (inFile.listFiles() == null) {
            System.out.println("apk文件夹下不存在需要打包带的文件");
            return null;
        }
        if (inFile.listFiles().length > 1) {
            System.out.print("请输入需要打包的文件所以(负数退出)：");
            int index = 0;
            for (File file : inFile.listFiles()) {
                if (file.isFile()) {
                    System.out.print(index + "：" + file.getName() + " ");
                    index++;
                }
            }
            Scanner scanner = new Scanner(System.in);
            int userInputIndex = scanner.nextInt();
            if (userInputIndex < 0) {
                return null;
            }
            if (userInputIndex > inFile.listFiles().length - 1) {
                System.out.println("索引不存在");
                readUserInputFile();
            } else {
                return inFile.listFiles()[userInputIndex];
            }
        } else if (inFile.listFiles().length == 1) {
            if (inFile.listFiles()[0].isFile()) {
                return inFile.listFiles()[0];
            } else {
                System.out.println("apk文件夹下不存在需要打包带的文件");
                return null;
            }
        }
        return null;
    }

    /**
     * 读取AndroidManiFest.xml文件，并将每一行存入集合中
     */
    private static void readAndroidManifile() {
        try {
            File file = new File("./tmp/"+Thread.currentThread().getName()+"/AndroidManifest.xml").getCanonicalFile();
            if (file == null) {
                return;
            }
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            if (androidManifileMap.get(Thread.currentThread().getName()) == null){
                androidManifileMap.put(Thread.currentThread().getName(), new ArrayList<>());
            }
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                List<String> list = androidManifileMap.get(Thread.currentThread().getName());
                list.add(line);
                androidManifileMap.put(Thread.currentThread().getName(), list);
                //读取app的icon名称
                if (line.trim().contains("<application") && line.contains("android:icon=")) {
                    String[] contentArray = line.split(" ");
                    for (String item : contentArray) {
                        if (item.startsWith("android:icon=")) {
                            iconPath = item.substring(item.indexOf("\"") + 1, item.lastIndexOf("\"")).replace("@", "");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File readUserInputConfigIndex() {
        try {
            File in = new File("./config").getCanonicalFile();
            if (in == null || in.listFiles() == null) {
                System.out.println("config文件夹下不存在需要进行多渠道打包的csv文件");
                return null;
            }
            if (in.listFiles().length > 1) {
                Scanner userInput = new Scanner(System.in);
                System.out.print("请输入需要使用的配置文件(负数退出)：");
                int fileIndex = 0;
                for (File file : in.listFiles()) {
                    if (file.isFile()) {
                        System.out.print(fileIndex + "：" + file.getName() + " ");
                        fileIndex++;
                    }
                }
                int userIn = userInput.nextInt();
                if (userIn < 0) {
                    return null;
                }
                if (userIn > in.listFiles().length - 1) {
                    System.out.println("不存在改索引文件");
                    readUserInputConfigIndex();
                } else {
                    File file = in.listFiles()[userIn];
                    if (file.isFile()) {
                        return file;
                    } else {
                        System.out.println("选中的不是文件，而是文件夹，请重新选择");
                        readUserInputConfigIndex();
                    }
                }
            } else {
                if (in.listFiles()[0].isFile()) {
                    return in.listFiles()[0].getCanonicalFile();
                } else {
                    System.out.println("config文件夹下不存在需要进行多渠道打包的csv文件");
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 读取配置文件
     */
    private static void readConfigFile() {
        try {
            //读取渠道，app名称，icon图标等配置信息
            File in = readUserInputConfigIndex();
            if (in == null) {
                return;
            }
            BufferedReader bufferedReader = new BufferedReader(new FileReader(in.getCanonicalFile()));
            int index = 0;
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                String[] content = line.split(",",-1);
                if (index == 0) {
                    //第一行为csv的标题
                    int tmpIndex = 0;
                    for (String item : content) {
                        configTitleMap.put(tmpIndex, item);
                        tmpIndex++;
                    }
                } else {
                    configList.add(content);
                }
                index++;
            }
            bufferedReader.close();

            //读取签名的配置文件
            index = 0;
            File keyConfig = new File("./key/keyConfig.csv").getCanonicalFile();
            BufferedReader keyReader = new BufferedReader(new FileReader(keyConfig));
            while (true) {
                String line = keyReader.readLine();
                if (line == null) {
                    break;
                }
                String[] content = line.split(",",-1);
                if (index == 0) {
                    //第一行为csv的标题
                    int tmpIndex = 0;
                    for (String item : content) {
                        keyConfigTitleMap.put(tmpIndex, item);
                        tmpIndex++;
                    }
                } else {
                    //这里获取默认的签名配置，不是默认配置签名，不进行载入
                    int tmpIndex = 0;
                    for (String keyItem : content) {
                        if (keyConfigTitleMap.get(tmpIndex).equalsIgnoreCase("default") && keyItem.equalsIgnoreCase("true")) {
                            int keyContentIndex = 0;
                            for (String keyContent : content) {
                                keyConfigMap.put(keyConfigTitleMap.get(keyContentIndex), keyContent);
                                keyContentIndex++;
                            }
                            break;
                        }
                        tmpIndex++;
                    }
                }
                index++;
            }
            keyReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 写入新的app名称
     */
    public static void makeNewAndroidManifest(int missionNumber) {
        System.out.println("missionNumber的值为："+missionNumber);
        try {
            //读取配置列表
            int configIndex = 0;
            String tmpName = "";
            String outputName = "";
            String[] item = configList.get(missionNumber);
            for (String config : item) {
                ListIterator<String> iterator = androidManifileMap.get(Thread.currentThread().getName()).listIterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    //System.out.println(configTitleMap.get(configIndex));
                    if (configTitleMap.get(configIndex).equals("appName")) {
                        if (config.isEmpty()) {
                            continue;
                        }
                        //System.out.println("进入名字修改环节");
                        //这个表示需要修改app名称
                        if (line.trim().contains("<application") && line.contains("android:label=")) {
                            if (tmpName.isEmpty()) {
                                tmpName = config + "_";
                            } else {
                                tmpName = tmpName + "_" + config;
                            }
                            //替换apk包名
                            String[] contentArray = line.split(" ");
                            line = "";
                            int index = 0;
                            for (String contentItem : contentArray) {
                                if (contentItem.startsWith("android:label=")) {
                                    if (contentItem.endsWith(">")) {
                                        contentArray[index] = "android:label=\"" + config + "\">";
                                    } else {
                                        contentArray[index] = "android:label=\"" + config + "\"";
                                    }
                                }
                                if (index <= contentArray.length - 1) {
                                    line = line + contentArray[index] + " ";
                                }
                                index++;
                            }
                            iterator.set(line);
                        }
                    } else if (configTitleMap.get(configIndex).equals("appIcon")) {
                        //表示需要修改app的Ionc
                        if (line.trim().contains("<application") && line.contains("android:icon=")) {
                            makeNewAppIcon(config);
                        }
                    } else if (configTitleMap.get(configIndex).endsWith("outName")) {
                        outputName = config;
                    } else {
                        //需要修改meta标签中的值
                        if (line.trim().contains("android:name=\"" + configTitleMap.get(configIndex) + "\"")) {
                            //替换apk包名
                            String[] contentArray = line.split(" ");
                            line = "";
                            int index = 0;
                            for (String contentItem : contentArray) {
                                if (contentItem.startsWith("android:value=")) {
                                    contentArray[index] = "android:value=\"" + config + "\"/>";
                                }
                                if (index <= contentArray.length - 1) {
                                    line = line + contentArray[index] + " ";
                                }
                                index++;
                            }
                            iterator.set(line);
                        }
                    }
                }
                configIndex++;
            }
            //生成新的AndroidManifest文件
            writeNewAndroidManifest();
            //重新打包生成apk
            System.out.println("重新打包中......");
            ApkOptions apkOptions = new ApkOptions();
            String out = "./resultApk/" + tmpName + "_" + System.currentTimeMillis()+"_"+outputName+ ".apk";
            //System.out.println(out);
            new Androlib(apkOptions).build(new File(outPath+Thread.currentThread().getName()), new File(out).getCanonicalFile());
            System.out.println("打包完成！");
            cachedThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    Utils.signApk(out,keyConfigMap.get("key"),keyConfigMap.get("password"),keyConfigMap.get("alias"));
                    System.out.println("完成进度：" + (signNum) + "/" + configList.size());
                    signNum++;
                    if (signNum == configList.size()){
                        long endTime = System.currentTimeMillis();
                        System.out.println("总打包个数：" + configList.size() + ",打包总耗时：" + ((endTime - startTime) / 1000) + "秒");
                        System.out.println("=================任务完成，程序结束===================");
                        System.exit(0);
                    }
                }
            });
            if (progress < configList.size()){
                progress++;
                //重新领取打包任务执行
                makeNewAndroidManifest(progress);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 生成新的AndroidManifest.xml文件
     */
    private static void writeNewAndroidManifest() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter((new File("./tmp/"+Thread.currentThread().getName()+"/AndroidManifest.xml")).getCanonicalFile()));
            for (String line : androidManifileMap.get(Thread.currentThread().getName())) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 替换app的icon
     */
    public static void makeNewAppIcon(String needIconName) {
        //System.out.println("需要的图片名称:"+needIconName);
        try {
            //System.out.println("图标名称：" + iconPath);
            //获取要修改的app图标
            File file = new File("./icon").getCanonicalFile();
            if (needIconName.isEmpty()) {
                //如果配置文件中没有icon，则需要将之前拷贝的图片重新拷贝回来
                file = new File("./icon/source").getCanonicalFile();
            }
            File[] files = file.listFiles();
            //保存hdpi对应目录下的icon
            final HashMap<String, File> fileMap = new HashMap<>();
            //获取icon目录
            for (File fileItem : files) {
                //获取icon目录下对应的hdpi类型
                if (!fileItem.isFile()) {
                    File[] fileChilds = fileItem.listFiles();
                    //遍历dpi下的icon
                    for (File item : fileChilds) {
                        if (item.isFile() && item.getName().equals(needIconName)) {
                            //只允许png,webp,jpg,jpeg这几种格式，其他格式不被支持。只读取第一张图片。
                            if (item.getName().endsWith(".png") || item.getName().endsWith(".webp")) {
                                fileMap.put(fileItem.getName(), item);
                            } else if (item.getName().endsWith("jpg") || item.getName().endsWith("jpeg")) {
                                System.out.println("不建议使用jpg或jpeg格式图片");
                                fileMap.put(fileItem.getName(), item);
                            }
                        }
                    }
                }
            }

            //进行icon的图片替换工作。这里对icon路径进行分析，判断icon在drawable目录还是mipmap目录,及获取icon文件的名称
            final String[] icon = iconPath.split("/");
            for (final String key : fileMap.keySet()) {
                try {
                    FileInputStream fils = new FileInputStream(fileMap.get(key));
                    //获取输入图片的后缀
                    String[] iconName = fileMap.get(key).getName().split("\\.");
                    if (icon[0].equals("drawable")) {
                        //如果存在同名但不同后缀的原始图片，需要删除掉这些图片
                        File webp = (new File("./tmp/"+Thread.currentThread().getName()+"/res/drawable-" + key + "/" + icon[1] + ".webp")).getCanonicalFile();
                        File jpg = (new File("./tmp/"+Thread.currentThread().getName()+"/res/drawable-" + key + "/" + icon[1] + ".jpg")).getCanonicalFile();
                        File jpeg = (new File("./tmp/"+Thread.currentThread().getName()+"/res/drawable-" + key + "/" + icon[1] + ".jpeg")).getCanonicalFile();
                        File png = (new File("./tmp/"+Thread.currentThread().getName()+"/res/drawable-" + key + "/" + icon[1] + ".png")).getCanonicalFile();
                        if (webp.exists()) {
                            webp.delete();
                        }
                        if (jpg.exists()) {
                            jpg.delete();
                        }
                        if (jpeg.exists()) {
                            jpeg.delete();
                        }
                        if (png.exists()) {
                            png.delete();
                        }
                        FileOutputStream out = new FileOutputStream((new File("./tmp/"+Thread.currentThread().getName()+"/res/drawable-" + key + "/" + icon[1] + "." + iconName[1] + "")).getCanonicalFile());
                        // 读取和写入信息
                        int len = 0;
                        while ((len = fils.read()) != -1) {
                            out.write(len);
                        }
                        out.close();
                        fils.close();
                    } else {
                        File webp = new File("./tmp/"+Thread.currentThread().getName()+"/res/mipmap-" + key + "/" + icon[1] + ".webp").getCanonicalFile();
                        File jpg = new File("./tmp/"+Thread.currentThread().getName()+"/res/mipmap-" + key + "/" + icon[1] + ".jpg").getCanonicalFile();
                        File jpeg = new File("./tmp/"+Thread.currentThread().getName()+"/res/mipmap-" + key + "/" + icon[1] + ".jpeg").getCanonicalFile();
                        File png = new File("./tmp/"+Thread.currentThread().getName()+"/res/mipmap-" + key + "/" + icon[1] + ".png").getCanonicalFile();
                        if (webp.exists()) {
                            webp.delete();
                        }
                        if (jpg.exists()) {
                            jpg.delete();
                        }
                        if (jpeg.exists()) {
                            jpeg.delete();
                        }
                        if (png.exists()) {
                            png.delete();
                        }
                        FileOutputStream out = new FileOutputStream("./tmp/"+Thread.currentThread().getName()+"/res/mipmap-" + key + "/" + icon[1] + "." + iconName[1] + "");
                        int len = 0;
                        while ((len = fils.read()) != -1) {
                            out.write(len);
                        }
                        out.close();
                        fils.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

