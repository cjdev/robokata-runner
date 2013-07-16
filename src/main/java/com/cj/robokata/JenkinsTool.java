package com.cj.robokata;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class JenkinsTool {

	private final String baseUrl;

	public JenkinsTool(String baseUrl) {
		super();
		this.baseUrl = baseUrl;
	}

	public class JobInfo {
		public final String name;
		public final JobStatus status;
		public JobInfo(String name, JobStatus status) {
			super();
			this.name = name;
			this.status = status;
		}
		
		@Override
		public String toString() {
			return name + ", status: " + status;
		}
	}
	
	public static class JobDetails {
		private final Integer lastBuild;
		private final Integer lastStableBuild;
		private final Integer lastFailedBuild;
		private final List<Integer> knownBuilds = new ArrayList<Integer>();
		
		public JobDetails(Integer lastBuild, Integer lastStableBuild,
				Integer lastFailedBuild, List<Integer> knownBuilds) {
			super();
			this.lastBuild = lastBuild;
			this.lastStableBuild = lastStableBuild;
			this.lastFailedBuild = lastFailedBuild;
			this.knownBuilds.addAll(knownBuilds);
		}
		public Integer lastBuild() {
			return lastBuild;
		}
		
		public List<Integer> knownBuilds() {
			return knownBuilds;
		}
		public Integer buildWhereTheJobStoppedBeingGreen() {
			if(lastStableBuild==null && lastBuild!=null){
				return lastBuild;
			}else if((lastStableBuild!=null && lastBuild!=null) && lastStableBuild < lastBuild){
				return lastStableBuild+1;
			}
			return null;
		}

		public static JobDetails read(Document dom) {
			Element tag = dom.getRootElement();
			Integer lastBuild = buildNumber("lastBuild", tag);
			Integer lastFailedBuild = buildNumber("lastFailedBuild", tag);//Integer.valueOf(tag.element("lastFailedBuild").elementText("number"));
			Integer lastStableBuild = buildNumber("lastStableBuild", tag);//Integer.valueOf(tag.element("lastStableBuild").elementText("number"));
			
			List<Integer> knownBuilds = new ArrayList<Integer>();
			for(Element next: (List<Element>)tag.elements()){
				Integer n = buildNumber(next);
				if(n!=null) knownBuilds.add(n);
			}
			return new JobDetails(lastBuild, lastStableBuild, lastFailedBuild, knownBuilds);
		}
		
		private static Integer buildNumber(String name, Element tag){
			Element e = tag.element(name);
			return buildNumber(e);
		}
		private static Integer buildNumber(Element e) {
			Integer i;
			if(e==null){
				i = null;
			}else{
				String text = e.elementText("number");
				i = text==null?null:Integer.parseInt(text);
			}
			return i;
		}
		
	}
	
	public enum JobStatus{
			DISABLED,
            NOTBUILT,
			ABORTED,
			RED, RED_ANIME, 
			GREY, GREY_ANIME,
			BLUE, BLUE_ANIME, 
			ABORTED_ANIME, 
			YELLOW_ANIME,
			YELLOW;
	}
	
	public List<JobInfo> getListOfJobs(){
		try {
			URL url = new URL(baseUrl + "/api/xml");
			Document dom = new SAXReader().read(url);
			List<JobInfo> jobs = new ArrayList<JobInfo>();
			
			for( Element job : (List<Element>)dom.getRootElement().elements("job")) {
				JobInfo info = readJobInfoFromXmlNode(job);
				jobs.add(info);
			}
			
			return jobs;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public List<URL> getListOfSuspects(JobInfo job, int buildNumber){
		try {
			URL url = new URL(baseUrl + "/job/" + job.name + "/" + buildNumber + "/api/xml");
			Document dom = new SAXReader().read(url);
			List<URL> suspects = new ArrayList<URL>();
			
			for( Element tag : (List<Element>)dom.getRootElement().elements("culprit")) {
				suspects.add(new URL(tag.elementText("absoluteUrl")));
			}
			
			return suspects;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static class UserInfo {
		public final String fullName;
		public final String absoluteUrl;
		private UserInfo(String absoluteUrl, String url) {
			super();
			this.fullName = absoluteUrl;
			this.absoluteUrl = url;
		}
		
	}
	
	public List<UserInfo> listUsers(){
		try {
			List<UserInfo> users = new ArrayList<JenkinsTool.UserInfo>();
			URL url = new URL(baseUrl + "/view/main/people/api/xml");
			Document dom = new SAXReader().read(url);
			Element root = dom.getRootElement();
			for(Element userTag : (List<Element>) root.elements("user")){
				Element innerUserTag = userTag.element("user");
				String fullName = innerUserTag.elementText("fullName");
				String absoluteUrl = innerUserTag.elementText("absoluteUrl");
				users.add(new UserInfo(fullName, absoluteUrl));
			}
			
			return users;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public UserDetails getUserDetails(URL ref){
		try {
			System.out.println("Getting user details from " + ref);
			Document dom = new SAXReader().read(new URL(ref.toString() + "/api/xml"));
			return UserDetails.read(dom);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
	}
	
	public static class UserDetails {
		public final String id, fullName;
		public final Map<String, String> properties;
		
		private UserDetails(String id, String fullName, Map<String, String> properties) {
			super();
			this.id = id;
			this.fullName = fullName;
			this.properties = properties;
		}
		
		
		public static UserDetails read(Document dom){
			Element tag = dom.getRootElement();
			
			final String id = tag.elementText("id");
			final String fullName = tag.elementText("fullName");
			final Map<String, String> properties = new HashMap<String, String>();
			
			for(Element propertyTag : (List<Element>) tag.elements("property")){
				for(Element value : (List<Element>) propertyTag.elements()){
					properties.put(value.getName(), value.getText());
				}
			}
			
			return new UserDetails(id, fullName, properties);
		}
	}
	
	private JobInfo readJobInfoFromXmlNode(Element job){
		return new JobInfo(job.elementText("name"), JobStatus.valueOf(job.elementText("color").toUpperCase()));
	}
	
	public JobInfo getInfoForJobNamed(String jobName){
		try {
			URL url = new URL(baseUrl + "/job/" + jobName + "/api/xml");
			Document dom = new SAXReader().read(url);
			return readJobInfoFromXmlNode(dom.getRootElement());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	public String humanUrlForJobNamed(String jobName) {
		return baseUrl + "/job/" + jobName;
	}
	

	public BuildInfo infoForLastBuildOfJobNamed(String jobName) {
		try {
			URL url = new URL(baseUrl + "/job/" + jobName + "/lastBuild/api/xml");
			Document dom = new SAXReader().read(url);
			
			return readBuildInfoFromXmlNode(dom.getRootElement());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private BuildInfo readBuildInfoFromXmlNode(Element build) {
		List<CulpritInfo> culprits = new ArrayList<JenkinsTool.CulpritInfo>();
		for( Element job : (List<Element>)build.elements("culprit")) {
			culprits.add(new CulpritInfo(job.elementText("fullName"), job.elementText("absoluteUrl")));
		}
		
		List<ArtifactInfo> artifacts = new ArrayList<ArtifactInfo>();
		
		for( Element artifact : (List<Element>)build.elements("artifact")) {
			artifacts.add(new ArtifactInfo(
							artifact.elementText("fileName"), 
							artifact.elementText("relativePath")
						));
		}
		
		return new BuildInfo(culprits, artifacts);
	}

	public static class CulpritInfo {
		final String fullName;
		final String url;
		
		private CulpritInfo(String fullName, String url) {
			super();
			this.fullName = fullName;
			this.url = url;
		}
		
	}
	
	public static class ArtifactInfo {
		public final String fileName, relativePath;

		public ArtifactInfo(String fileName, String relativePath) {
			super();
			this.fileName = fileName;
			this.relativePath = relativePath;
		}
		
	}
	
	public static class BuildInfo {
		public final List<CulpritInfo> culprits;
		public final List<ArtifactInfo> artifacts;
		
		private BuildInfo(List<CulpritInfo> culprits, final List<ArtifactInfo> artifacts) {
			super();
			this.culprits = culprits;
			this.artifacts = artifacts;
		}
	}

	public JobDetails getDetailsForJobNamed(String jobName) {
		try {
			URL url = new URL(baseUrl + "/job/" + jobName + "/api/xml");
			Document dom = new SAXReader().read(url);
			
			return JobDetails.read(dom);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
