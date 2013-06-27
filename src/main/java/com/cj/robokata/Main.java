package com.cj.robokata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.joda.time.TimeOfDay;
import org.joda.time.YearMonthDay;
import org.joda.time.format.DateTimeFormat;

import com.cj.robokata.JenkinsTool.ArtifactInfo;
import com.cj.robokata.JenkinsTool.BuildInfo;
import com.cj.robokata.JenkinsTool.JobInfo;
import com.cj.robokata.JenkinsTool.JobStatus;

import robocode.Robot;

public class Main {
	static interface JarReference {
		URL url();
		String name();
	}
	
	static class LocalJar implements JarReference {
		private final File path;
		
		public LocalJar(File path) {
			super();
			this.path = path;
		}
		
		@Override
		public String name() {
			return path.getName();
		}
		@Override
		public URL url() {
			try {
				return path.toURL();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	static class JenkinsJar implements JarReference {
		private final URL url;
		private final ArtifactInfo info;
		public JenkinsJar(String url, ArtifactInfo info) {
			super();
			try {
				this.url = new URL(url);
				this.info = info;
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
		public URL url() {
			return url;
		}
		@Override
		public String name() {
			return info.fileName;
		}
	}
	
	static class RobotEntry {
		final JarReference contestant;
		final String className;
		
		public RobotEntry(JarReference contestant, String className) {
			super();
			this.contestant = contestant;
			this.className = className;
		}
	}
	
	public static void main(String[] args) throws Exception {
		if(args.length!=1){
			System.out.println("Usage: robokata-runner http://your-jenkins-host");
			System.exit(-100);
		}
		
		String jenkinsBaseUrl = args[0];
		File localPath = new File("kata-" + new YearMonthDay() + "-" + new TimeOfDay().toString(DateTimeFormat.forPattern("HH-mm-ss")));
		
		mkdirs(localPath);
		
		@SuppressWarnings("deprecation")
		YearMonthDay todaysDate = new YearMonthDay();
		
		List<JarReference> jars = new ArrayList<JarReference>();
		
		addLocalJars(args, jars);
		
		addJenkinsJars(jenkinsBaseUrl, todaysDate, jars);
		
		
		if(jars.isEmpty()){
			System.out.println("Sorry, there were no contestants.  Everybody loses, especially you!");
		}else{
			try {
				System.out.println("Found " + jars.size() + " candidates.  Starting game, prepare to be ASTOUNDED");
				runGame(localPath, jars, todaysDate);
			} catch (Exception e) {
				System.out.println("There was an error :'(");
				e.printStackTrace(System.out);
			}
		}
	}

	private static void addLocalJars(String[] args, List<JarReference> jars) {
		for(int x=0;x<args.length;x++){
			File path = new File(args[x]);
			if(path.exists() && path.isFile()){
				jars.add(new LocalJar(path));
			}
		}
	}

	private static void addJenkinsJars(String jenkinsBaseUrl,
			YearMonthDay todaysDate, List<JarReference> jars) {
		try {
			JenkinsTool jenkins = new JenkinsTool(jenkinsBaseUrl);
			
			
			for(JobInfo job : findCandidates(jenkins, todaysDate)){
				BuildInfo build = jenkins.infoForLastBuildOfJobNamed(job.name);
				
				if(build.artifacts.isEmpty()){
					System.out.println(job.name + " has no archived artifacts, so it is DISQUALIFIED!");
				}else{
					for(ArtifactInfo info : build.artifacts) {
						jars.add(new JenkinsJar(
								jenkinsBaseUrl + "/job/" + job.name + "/lastBuild/artifact/" + info.relativePath,
								info));
					}					
				}
				
			}
		} catch (Exception e1) {
			System.out.println("Game setup error ;) ");
			e1.printStackTrace(System.out);
		}
	}

	private static void runGame(File localPath, List<JarReference> jars, final YearMonthDay todaysDate) {
		try {
			
			
			File jarsDir = new File(localPath, "jars");

			File robocode = new File(localPath, "robocode");
			robocode.mkdir();
			
			
			InputStream data = Main.class.getResourceAsStream("/robocode-1.7.3.2-setup.jar");
			
			File temp = writeToTempFile(data);
			
			
			unzip(temp, robocode);
			
			File battlesDir = new File(robocode, "battles");
			File robotsDir = new File(robocode, "robots");
			
			
			prepCleanDirectory(jarsDir);
			prepCleanDirectory(robotsDir);
			
			
			
			
			Map<String, RobotEntry> contestantsByJar = new HashMap<String, RobotEntry>();
			for(JarReference next : jars){
				final String id = next.url().toString();
				
				try {
					File localPathToJar;
					URL source = next.url();
					
					if(source.getProtocol().equals("file")){
						localPathToJar = new File(source.getPath());
					}else{
						localPathToJar = new File(jarsDir,next.name());
						get(source, localPathToJar);
					}
					
					unzip(localPathToJar, robotsDir);
					
					for(String robotClass : findRobotClasses(localPathToJar)){
						RobotEntry entry = contestantsByJar.get(id);
						if(entry==null){
							contestantsByJar.put(id, new RobotEntry(next, robotClass));
						}else{
							System.out.println("Ignoring " + robotClass + " because there is already a contestant for " + id + " (" + entry.className + ") QUIT CHEATING!");
						}
						
					}
				} catch (Exception e) {
					System.out.println("There was an error processing jar '" + id + "'.  Jar is disqualified, and loses.");
					e.printStackTrace(System.out);
				}
				
			}
			
			System.out.println("Starting battle");
			File battleConfig = new File(battlesDir, "robokata-battle-" + todaysDate + ".battle");
			
			renderBattleConfig(contestantsByJar.values(), battleConfig);
			
			File launchScript = findLaunchScriptForCurrentPlatform(robocode);
			
			launchScript.setExecutable(true);
			
			new CommandRunnerImpl(robocode).runPassThrough(launchScript.getAbsolutePath(), "-battle", battlesDir.getName() + "/" + battleConfig.getName());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static File findLaunchScriptForCurrentPlatform(File robocode) {
		File launchScript;
		
		if(System.getProperty("os.name").toLowerCase().contains("windows")){
			launchScript = new File(robocode, "robocode.bat");
		}else{
			launchScript= new File(robocode, "robocode.sh");
		}
		return launchScript;
	}

	private static File tempDir(File where) throws IOException {
		File dest = File.createTempFile("robocode-1.7.3.2", ".dir", where);
		dest.delete();
		mkdirs(dest);
		return dest;
	}

	private static void unzip(File temp, File dest) throws ZipException,
			IOException, FileNotFoundException {
		ZipFile z = new ZipFile(temp);
		Enumeration<? extends ZipEntry> entries = z.entries();
		
		while(entries.hasMoreElements()){
			ZipEntry next = entries.nextElement();
			File x = new File(dest, next.getName());
			
			if(next.getName().isEmpty()){
				System.out.println("[UNZIP] Zip entry has empty name ... wierd ... ignoring it :'( .");
			}else{
				System.out.println("[UNZIP] " + next.getName() + " to " + x.getAbsolutePath());
				if(next.isDirectory()){
					mkdirs(x);
				}else{
					FileOutputStream out = new FileOutputStream(x);
					IOUtils.copy(z.getInputStream(next), out);
					out.close();
				}
			}
			
			
		}
	}

	private static void mkdirs(File x) {
		if(!x.exists() && !x.mkdirs()){
			throw new RuntimeException("Could not create directory " + x.getAbsolutePath());
		}
	}

	private static File writeToTempFile(InputStream data) throws IOException,
			FileNotFoundException {
		File temp = File.createTempFile("robocode-1.7.3.2", ".zip");
		FileOutputStream tempOut = new FileOutputStream(temp);
		IOUtils.copy(
					data, 
					tempOut
				);
		tempOut.close();
		return temp;
	}

	private static void renderBattleConfig(Collection<RobotEntry> robots,
			File battleConfig) {
		createFile(battleConfig);
		
		Properties props = new Properties();
		props.put("robocode.battle.numRounds", "3");
		props.put("robocode.battle.gunCoolingRate", "0.1");
		props.put("robocode.battleField.width", "800");
		props.put("robocode.battle.rules.inactivityTime", "450");
		
		StringBuilder text = new StringBuilder();
		for(RobotEntry next : robots){
			if(text.length()>0){
				text.append(",");
			}
			text.append(next.className + "*");
			//"stu.Robot*"
		}
		
		props.put("robocode.battle.selectedRobots", text.toString());
		props.put("robocode.battle.hideEnemyNames", "false");
		props.put("robocode.battleField.height", "600");
		
		write(battleConfig, props);
	}

	private static List<String> findRobotClasses(File localPathToJar) {
		List<String> robots = new ArrayList<String>();
		try {
			URLClassLoader cl = new URLClassLoader(new URL[]{localPathToJar.toURL()}, Main.class.getClassLoader());
			Class<Robot> c = Robot.class;
			ZipFile zip = new ZipFile(localPathToJar);
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while(entries.hasMoreElements()){
				ZipEntry next = entries.nextElement();
				String name = next.getName();
				
				if(name.endsWith(".class")){
					System.out.println("Found: " + name);
					name = name.replaceAll(Pattern.quote(".class"), "").replaceAll(Pattern.quote("/"), ".");
					Class<?> z = cl.loadClass(name);
					System.out.println("Loaded class: " + z.getName());
					if(c.isAssignableFrom(z)){
						robots.add(name);
					}
				}
				
			}
		} catch (Throwable e) {
			System.out.println("Error reading " + localPathToJar.getAbsolutePath());
			e.printStackTrace(System.out);
		}
		return robots;
	}
	
	

	private static void createFile(File battleConfig) {
		String msg = "Unable to create file: " + battleConfig.getAbsolutePath();
		
		try {
			if(!battleConfig.exists() && !battleConfig.createNewFile()){
				throw new RuntimeException(msg);
			}
		} catch (IOException e) {
			throw new RuntimeException(msg, e);
		}
	}

	private static void write(File battleConfig, Properties props) {
		try {
			FileOutputStream battleConfigOut = new FileOutputStream(battleConfig);
			props.store(battleConfigOut, "whatever");
			battleConfigOut.flush();
			battleConfigOut.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void prepCleanDirectory(File localPath) throws IOException {
		FileUtils.forceMkdir(localPath);
		FileUtils.cleanDirectory(localPath);
	}

	private static void get(URL url, File dest) {
		try{
			System.out.println("Downloading " + url + " to " + dest.getAbsolutePath());
			FileOutputStream out = new FileOutputStream(dest);
			IOUtils.copy(url.openStream(), out);
			out.close();
			
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	private static List<JobInfo> findCandidates(JenkinsTool jenkins, YearMonthDay todaysDate) {
		List<JobInfo> candidates = new LinkedList<JobInfo>();
		
		for(JobInfo job : jenkins.getListOfJobs()){
			if(matchesNameFilter(todaysDate, job)){
				System.out.println("Found job: " + job.name);
				if(job.status == JobStatus.BLUE){
					candidates.add(job);
				}else{
					System.out.println(job.name + " is disqualified because it is broken! :'(");
				}
			}
		}
		return candidates;
	}

	private static boolean matchesNameFilter(YearMonthDay todaysDate,
			JobInfo job) {
		return job.name.startsWith("robokata-" + todaysDate);
	}
}
