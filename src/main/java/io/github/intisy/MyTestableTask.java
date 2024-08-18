package io.github.intisy;

import io.github.intisy.impl.FileCreator;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class MyTestableTask extends DefaultTask {
    private File outputFile;

    public MyTestableTask() {
        this.outputFile = new File(getProject().getBuildDir(), "myfile.txt");
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    public void action() throws IOException {
        FileCreator creator = new FileCreator(outputFile);
        creator.create();
    }
}
