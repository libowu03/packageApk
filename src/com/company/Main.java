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
    //AndroidManifest文件的所有文本内容，包含了每一行内容
    private static List<String> androidManifile = new ArrayList<>();
    //csv第几列对应的标题
    private static HashMap<Integer, String> configTitleMap = new HashMap<>();
    //签名配置文件的标题
    private static HashMap<Integer, String> keyConfigTitleMap = new HashMap<>();
    //签名配置文件的具体内容
    private static HashMap<String, String> keyConfigMap = new HashMap<>();
    //反编译后临时数据存放地点
    private static String outPath = "./tmp/out";
    //目标apk存放路径
    private static String inputPath = "./apk";
    //打包进度
    private static int progress = 0;
    //是否需要复用上次反编译的文件
    private static boolean isNeedPreviousFile = false;
    //开始时间
    private static long startTime;

    public static void main(String[] args) {
        for (String item:args){
            if (item.equals("-n")){
                isNeedPreviousFile = true;
            }
        }
        startTime = System.currentTimeMillis();
        try {
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
            if (!isNeedPreviousFile){
                System.out.println("执行反编译工作.........");
                File out = new File(outPath);
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

        System.out.println("读取AndroidManiFest.xml文件中......");
        readAndroidManifile();
        copySourceIcon();
        System.out.println("读取AndroidManiFest.xml文件完成！");

        System.out.println("修改并生成新的apk中......");
        makeNewAndroidManifest();
        System.out.println("任务完成！");
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
    private static void copySourceIcon() {
        try {
            File resList = new File("./tmp/out/res");
            if (resList != null && resList.listFiles() != null) {
                //进行icon的图片替换工作。这里对icon路径进行分析，判断icon在drawable目录还是mipmap目录,及获取icon文件的名称
                final String[] icon = iconPath.split("/");
                System.out.println(icon[0]);
                System.out.println(icon[1]);
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
            FileInputStream fils = new FileInputStream((new File("./tmp/out/res/" + (iconType + "-" + folder + "") + "/" + iconName)).getCanonicalFile());
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
            File file = new File("./tmp/out/AndroidManifest.xml").getCanonicalFile();
            if (file == null) {
                return;
            }
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                androidManifile.add(line);
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
    public static void makeNewAndroidManifest() {
        try {
            //读取配置列表
            for (String[] item : configList) {
                int configIndex = 0;
                String tmpName = "";
                String outputName = "";
                for (String config : item) {
                    ListIterator<String> iterator = androidManifile.listIterator();
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
                new Androlib(apkOptions).build(new File(outPath), new File(out).getCanonicalFile());
                System.out.println("打包完成！");
                cachedThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        signApk(out);
                        System.out.println("完成进度：" + (progress) + "/" + configList.size());
                        if (progress == configList.size()){
                            long endTime = System.currentTimeMillis();
                            System.out.println("总打包个数：" + configList.size() + ",打包总耗时：" + ((endTime - startTime) / 1000) + "秒");
                            System.out.println("=================任务完成，程序结束===================");
                            System.exit(0);
                        }
                    }
                });
                progress++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 调用系统命令给apk进行签名
     */
    private static void signApk(String apkName) {
        String keyPath = "./key/" + keyConfigMap.get("key");
        String keyPwd = keyConfigMap.get("password");
        String keyOtherName = keyConfigMap.get("alias");
        String out = apkName.replace(".apk", "_out.apk");
        //String cmd = "jarsigner -verbose -keystore 你的签名文件 -storepass 签名文件密码 -signedjar 签名后的apk名称 -digestalg SHA1 -sigalg MD5withRSA 待签名的apk  签名文件别名";
        String cmd = "jarsigner -verbose -keystore " + keyPath + " -storepass " + keyPwd + " -signedjar " + out + " -digestalg SHA1 -sigalg MD5withRSA " + apkName + "  " + keyOtherName + "";
        Runtime run = Runtime.getRuntime();//返回与当前 Java 应用程序相关的运行时对象
        try {
            Process p = run.exec(cmd);// 启动另一个进程来执行命令
            BufferedInputStream in = new BufferedInputStream(p.getInputStream());
            BufferedReader inBr = new BufferedReader(new InputStreamReader(in));
            String lineStr;
            while ((lineStr = inBr.readLine()) != null)
                //获得命令执行后在控制台的输出信息
                System.out.println(lineStr);// 打印输出信息
            //检查命令是否执行失败。
            if (p.waitFor() != 0) {
                if (p.exitValue() == 1)//p.exitValue()==0表示正常结束，1：非正常结束
                    System.err.println("命令签名失败!");
            }
            inBr.close();
            in.close();
            //删除掉未签名文件
            File file = (new File(apkName)).getCanonicalFile();
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成新的AndroidManifest.xml文件
     */
    private static void writeNewAndroidManifest() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter((new File("./tmp/out/AndroidManifest.xml")).getCanonicalFile()));
            for (String line : androidManifile) {
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
                        File webp = (new File("./tmp/out/res/drawable-" + key + "/" + icon[1] + ".webp")).getCanonicalFile();
                        File jpg = (new File("./tmp/out/res/drawable-" + key + "/" + icon[1] + ".jpg")).getCanonicalFile();
                        File jpeg = (new File("./tmp/out/res/drawable-" + key + "/" + icon[1] + ".jpeg")).getCanonicalFile();
                        File png = (new File("./tmp/out/res/drawable-" + key + "/" + icon[1] + ".png")).getCanonicalFile();
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
                        FileOutputStream out = new FileOutputStream((new File("./tmp/out/res/drawable-" + key + "/" + icon[1] + "." + iconName[1] + "")).getCanonicalFile());
                        // 读取和写入信息
                        int len = 0;
                        while ((len = fils.read()) != -1) {
                            out.write(len);
                        }
                        out.close();
                        fils.close();
                    } else {
                        File webp = new File("./tmp/out/res/mipmap-" + key + "/" + icon[1] + ".webp").getCanonicalFile();
                        File jpg = new File("./tmp/out/res/mipmap-" + key + "/" + icon[1] + ".jpg").getCanonicalFile();
                        File jpeg = new File("./tmp/out/res/mipmap-" + key + "/" + icon[1] + ".jpeg").getCanonicalFile();
                        File png = new File("./tmp/out/res/mipmap-" + key + "/" + icon[1] + ".png").getCanonicalFile();
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
                        FileOutputStream out = new FileOutputStream("./tmp/out/res/mipmap-" + key + "/" + icon[1] + "." + iconName[1] + "");
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

