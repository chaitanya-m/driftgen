package moa.classifiers.trees;

import com.github.javacliparser.IntOption;

import moa.classifiers.Classifier;

public interface ARFBaseTree extends Classifier{
	void setSubspaceSizeOption(int subspaceSize);
}
