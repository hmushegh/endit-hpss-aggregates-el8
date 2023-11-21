package Application;

/**
* The class used to set/get parameters from HPSS.
* 
* @author Haykuhi Musheghyan, <haykuhi.musheghyan@kit.edu>, KIT
* @year 2021
*/

public class HPSSFileInfo {
	
	private String fileHpssName;
	private String tapeName;
	private long fileSize;
	private int relPosition;
	private int cosId;
	private long offset;
	  
	public String getFileHpssName() {
		return fileHpssName;
	}
	public void setFileHpssName(String str) {
		this.fileHpssName = str;
	}
	public String getTapeName() {
		return tapeName;
	}
	public void setTapeName(String tapeName) {
		this.tapeName = tapeName;
	}
	public long getFilesize() {
		return fileSize;
	}
	public void setFilesize(long filesize) {
		this.fileSize = filesize;
	}
	public int getRelPosition() {
		return relPosition;
	}
	public void setRelPosition(int relPosition) {
		this.relPosition = relPosition;
	}
	public int getCOSId() {
		return cosId;
	}
	public void setCOSId(int cOSId) {
		cosId = cOSId;
	}
	public long getOffset() {
		return offset;
	}
	public void setOffset(long offset) {
		this.offset = offset;
	}	
	
}
