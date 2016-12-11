package moa.streams.generators.monash;

public class AttrVal {

	private int attr;
	private int val;

	AttrVal(int attribute, int value){
		attr = attribute;
		val = value;
	}

	public int getVal() {
		return val;
	}

	public int getAttr() {
		return attr;
	}

	public void setVal(int value) {
		val = value;
	}

	public void getAttr(int attribute) {
		attr = attribute;
	}

}
