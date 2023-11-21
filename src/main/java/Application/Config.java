package Application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Properties;

/**
* The class used to read the parameters written in the "/etc/endit-hpss-aggregates.properties" file.
* 
*   
* @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
* @year 2021
*/

public class Config {
	
	private Properties prop;		
	private Path [] readRequestDir;
	private File fileListDirFinished;
	private File fileListDirActive;
	private int aggregateListTime; // in Minutes
	private String keyTabFile;
	private String user;
	private int dirCleanupDays; // in days
	private int tapeDrives;
	private int retry;
	private long retryInterval;	// in seconds

	private Path [] writeRequestDir;
	
	private String vo;
	private int cosID;
	private String famPattern;
	
	private String [] DATAPaths;
	private String [] MCPaths;
	
	private int DATAPathsNr;
	private int MCPathsNr;
	
	private int DATAFFCount;
	private int MCFFCount;
	
	private int limitNrWriteRequests;	
	private String writeBashScript;
	private boolean purgeFileFromHPSS;
	private boolean p2Pmove;
	private int bufferSize;
	
	
	// Loads parameters from the "/etc/endit-hpss-aggregates.properties" file
    public Config () {
		 prop = new Properties();		 
		 try {
			prop.load(new FileInputStream("/etc/endit-hpss-aggregates.properties"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		 
    }
			public Path [] getReadRequestDir() {
				String [] path = prop.getProperty("read.request.dir.path").split(";");
				readRequestDir = new Path [path.length];
				for (int i = 0; i < path.length; i++)   	
					this.readRequestDir[i] =  FileSystems.getDefault().getPath(path[i]); 
	
				return this.readRequestDir;
			}
			
			
			public String getWriteBashScript() {
				this.writeBashScript = prop.getProperty("write.bash.script");
				return this.writeBashScript;
			}
			
			
			public File getFileListDirFinished() {
				this.fileListDirFinished = new File (prop.getProperty("filelist.dir.finished"));
				return this.fileListDirFinished;
			}
			
			public File getFileListDirActive() {
				this.fileListDirActive = new File (prop.getProperty("filelist.dir.active"));
				return this.fileListDirActive;
			}
			
			
			public int getAggregateListTime() {
				this.aggregateListTime = Integer.parseInt(prop.getProperty("make.list.time"));
				return this.aggregateListTime;
			}
			
			
			public int getDirCleanupDays() {
				this.dirCleanupDays = Integer.parseInt(prop.getProperty("dir.cleanup.days"));
				return this.dirCleanupDays;
			}
			
			public int getNrTapeDrives() {
				this.tapeDrives = Integer.parseInt(prop.getProperty("nr.tape.drives"));
				return this.tapeDrives;
			}
			
				
			public String getKeyTabFile() {
				this.keyTabFile = prop.getProperty("hpss.keytab.file");
				return this.keyTabFile;
			}
			
			
			public String getUser() {
				this.user = prop.getProperty("hpss.user");
				return this.user;
			}
			
			
			public int getRetry() {
				retry = Integer.parseInt(prop.getProperty("nr.retries"));
				return retry;
			}

			
			public long getRetryInterval() {
				retryInterval = Long.parseLong(prop.getProperty("retries.interval"));
				return retryInterval;
			}
			
		
			public Path [] getWriteRequestDir() {
				String [] path = prop.getProperty("write.request.dir.path").split(";");
				writeRequestDir = new Path [path.length];
				for (int i = 0; i < path.length; i++)   	
					this.writeRequestDir[i] =  FileSystems.getDefault().getPath(path[i]); 
	
				return this.writeRequestDir;
			}

			public int getCOSId() {
				this.cosID = Integer.parseInt(prop.getProperty("hpss.cosid"));
				return this.cosID;
			}
			
			public String getFAMPattern() {
				this.famPattern = prop.getProperty("hpss.file.fam.pattern");
				return this.famPattern;
			}
			
			public String[] getDATAPaths() {
				this.DATAPaths = prop.getProperty("DATA.path").split(";");
				return this.DATAPaths;
			}
			
			public String[] getMCPaths() {
				this.MCPaths = prop.getProperty("MC.path").split(";");
				return this.MCPaths;
			}

			public String getVo() {
				this.vo = prop.getProperty("vo");
				return this.vo;
			}

			public int getDATAPathsNr() {
				this.DATAPathsNr = Integer.parseInt(prop.getProperty("DATA.path.number"));
				return this.DATAPathsNr;
			}

			public int getMCPathsNr() {
				this.MCPathsNr = Integer.parseInt(prop.getProperty("MC.path.number"));
				return this.MCPathsNr;
			}

			public int getDATAFFCount() {
				this.DATAFFCount = Integer.parseInt(prop.getProperty("DATA.ff.count"));
				return this.DATAFFCount;
			}

			public int getMCFFCount() {
				this.MCFFCount = Integer.parseInt(prop.getProperty("MC.ff.count"));
				return MCFFCount;
			}

			public int getNrWriteRequests() {
				this.limitNrWriteRequests = Integer.parseInt(prop.getProperty("limit.nr.write.requests"));
				return limitNrWriteRequests;
			}
			
			public boolean isPurgingEnabled() {
				this.purgeFileFromHPSS = Boolean.parseBoolean(prop.getProperty("on.off.purging"));
				return this.purgeFileFromHPSS;
			}
			
			public boolean isP2PMoveEnabled() {
				this.p2Pmove = Boolean.parseBoolean(prop.getProperty("on.off.P2P.move"));
				return this.p2Pmove;
			}
			
			public int getBufferSize() {
				this.bufferSize = Integer.parseInt(prop.getProperty("buffer.size"));
				return this.bufferSize;
			}
}
