package Application;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.Date;

/**
 * The class used to set/get important parameters of the dictionary. 
 * 
 *   
 * @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
 * @year 2021
 */

public class DictObject {
	
	private String fileName;
	private String hpssFileName;
	private long fileSize;
    private Date dt;
    private String checksumType;
    private String checksumValue;
    private int exitValue;
    private int FF;
    private Path inDir;
    private Path reqDir;
    private Path outDir;
	
public DictObject (){
	      
	}
	
	public void setTime(Date currentDate ){      
		Calendar cal = Calendar.getInstance();
		cal.setTime(currentDate);
		dt= cal.getTime();
	}
	
	public Date getTime(){
		return this.dt;
   }

	public String getHpssFileName() {
		return hpssFileName;
	}

	public void setHpssFileName(String hpssFName) {
		this.hpssFileName = hpssFName;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}
	
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fName) {
		this.fileName = fName;
	}

	public String getChecksumType() {
		return checksumType;
	}

	public void setChecksumType(String checksumType) {
		this.checksumType = checksumType;
	}

	public String getChecksumValue() {
		return checksumValue;
	}

	public void setChecksumValue(String checksumValue) {
		this.checksumValue = checksumValue;
	}

	public int getExitValue() {
		return exitValue;
	}

	public void setExitValue(int exitValue) {
		this.exitValue = exitValue;
	}

	public int getFF() {
		return FF;
	}

	public void setFF(int fF) {
		FF = fF;
	}

	public Path getInDir() {
		return inDir;
	}

	public void setInDir(Path inDir) {
		this.inDir = inDir;
	}

	public Path getReqDir() {
		return reqDir;
	}

	public void setReqDir(Path reqDir) {
		this.reqDir = reqDir;
	}
	
	public Path getOutDir() {
		return outDir;
	}

	public void setOutDir(Path outDir) {
		this.outDir = outDir;
	}

}
