package edu.ucla.cs;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;

import java.util.function.Consumer;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.io.IOException;
import java.time.Duration;

import java.util.Arrays;
import org.datavec.api.io.filters.BalancedPathFilter;
import org.datavec.api.io.labels.ParentPathLabelGenerator;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.image.loader.LFWLoader;
import org.datavec.image.recordreader.ImageRecordReader;
import org.datavec.image.transform.ImageTransform;
import org.datavec.image.transform.PipelineImageTransform;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.LFWDataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.records.reader.impl.transform.TransformProcessRecordReader;

import static org.nd4j.linalg.indexing.NDArrayIndex.all;
import static org.nd4j.linalg.indexing.NDArrayIndex.interval;
import static org.nd4j.linalg.indexing.NDArrayIndex.point;

import com.google.flatbuffers.FlatBufferBuilder;

import edu.berkeley.cs.jqf.fuzz.guidance.Guidance;
import edu.berkeley.cs.jqf.fuzz.guidance.GuidanceException;
import edu.berkeley.cs.jqf.fuzz.guidance.Result;
import edu.berkeley.cs.jqf.fuzz.guidance.TimeoutException;
import edu.berkeley.cs.jqf.fuzz.util.Hashing;
import edu.berkeley.cs.jqf.instrument.tracing.events.BranchEvent;
import edu.berkeley.cs.jqf.instrument.tracing.events.CallEvent;
import edu.berkeley.cs.jqf.instrument.tracing.events.TraceEvent;
import edu.berkeley.cs.jqf.fuzz.util.Coverage;
import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance;

import java.nio.ByteBuffer;


public class TfGuidance extends ZestGuidance implements Guidance
{
    File inputDirectory;
    RecordReaderDataSetIterator iterator;
    ByteBuffer buffer;
    INDArray data;

    public class ByteBufferBackedInputStream extends InputStream {

        int index;
        INDArray data;

        public ByteBufferBackedInputStream(INDArray buf) {
            this.data = buf.ravel().permute(0, 2, 3, 1).ravel();
            index = 0;
        }

        public int read() throws IOException {
            assert(false);
            return 220;
        }

        public int read(byte[] bytes, int off, int len)
                throws IOException {
                //@TODO fixme
                assert(len == 4);

                int data = this.data.getInt(index++);

                byte[] arr = ByteBuffer.allocate(4).putInt(data).array();

                for (int i = 0; i < 4; i++) {
                    bytes[i] = arr[i];
                }

                return len;
        }
    }


    InputStream s;
    public TfGuidance(String testName, Duration duration, Long trials, File outputDirectory, File inputDirectory, Random sourceOfRandomness) throws IOException {
        super(testName, duration, trials, outputDirectory, inputDirectory, sourceOfRandomness);
        System.setProperty("jqf.ei.MAX_INPUT_SIZE", "999999999");
        data = null;
        this.inputDirectory = inputDirectory;

        //Transform t = new Transform(sourceOfRandomness);

        iterator = new LFWDataSetIterator(LFWLoader.SUB_NUM_IMAGES, LFWLoader.SUB_NUM_IMAGES, new int[]{256,256,3}, LFWLoader.SUB_NUM_LABELS, false, new ParentPathLabelGenerator(), true, 1, null, sourceOfRandomness);
    }

    /**
     * return next file from the directory
     */
    public InputStream getInput() {
        runCoverage.clear();

        if (data == null) {
            data = iterator.next().getFeatures();
        }

        return new ByteBufferBackedInputStream(data);
    }

    /**
     * true until there are no more files in the directory
     */
    public boolean hasInput() {
        return true;
    }

    @Override
    public void handleResult(Result result, Throwable error) throws GuidanceException {
            // Increment run count
            this.numTrials++;

            boolean valid = result == Result.SUCCESS;

            //System.out.println(error.getMessage());
            if (valid) {
                // Increment valid counter
                numValid++;
            }

            if (result == Result.SUCCESS || (result == Result.INVALID && !SAVE_ONLY_VALID)) {

                // Compute a list of keys for which this input can assume responsibility.
                // Newly covered branches are always included.
                // Existing branches *may* be included, depending on the heuristics used.
                // A valid input will steal responsibility from invalid inputs
                Set<Object> responsibilities = computeResponsibilities(valid);

                // Determine if this input should be saved
                List<String> savingCriteriaSatisfied = checkSavingCriteriaSatisfied(result);
                boolean toSave = savingCriteriaSatisfied.size() > 0;

                if (toSave) {
                    String why = String.join(" ", savingCriteriaSatisfied);

                    // Trim input (remove unused keys)
                    //currentInput.gc();

                    // It must still be non-empty
                    //assert (currentInput.size() > 0) : String.format("Empty input: %s", currentInput.desc);

                    // libFuzzerCompat stats are only displayed when they hit new coverage
                    if (LIBFUZZER_COMPAT_OUTPUT) {
                        displayStats();
                    }

                    /*
                    infoLog("Saving new input (at run %d): " +
                                    "input #%d " +
                                    "of size %d; " +
                                    "reason = %s",
                            numTrials,
                            savedInputs.size(),
                            currentInput.size(),
                            why);

                            */
                    // Save input to queue and to disk
                    /*
                    final String reason = why;
                    GuidanceException.wrap(() -> saveCurrentInput(responsibilities, reason));
                    */

                    // Update coverage information
                    updateCoverageFile();
                }
            } else if (result == Result.FAILURE || result == Result.TIMEOUT) {
                String msg = error.getMessage();

                // Get the root cause of the failure
                Throwable rootCause = error;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }

                // Attempt to add this to the set of unique failures
                rootCause.printStackTrace();
                if (uniqueFailures.add(Arrays.asList(rootCause.getStackTrace()))) {

                    // Trim input (remove unused keys)
                    //currentInput.gc();

                    // It must still be non-empty
                    //assert (currentInput.size() > 0) : String.format("Empty input: %s", currentInput.desc);

                    // Save crash to disk
                    int crashIdx = uniqueFailures.size() - 1;
                    String saveFileName = String.format("id_%06d", crashIdx);
                    File saveFile = new File(savedFailuresDirectory, saveFileName);
                    //GuidanceException.wrap(() -> writeCurrentInputToFile(saveFile));
                    infoLog("%s", "Found crash: " + error.getClass() + " - " + (msg != null ? msg : ""));
                    infoLog("%s", error.getStackTrace().toString());
                    //String how = currentInput.desc;
                    String why = result == Result.FAILURE ? "+crash" : "+hang";
                    //infoLog("Saved - %s %s %s", saveFile.getPath(), how, why);

                    if (EXACT_CRASH_PATH != null && !EXACT_CRASH_PATH.equals("")) {
                        File exactCrashFile = new File(EXACT_CRASH_PATH);
                        //GuidanceException.wrap(() -> writeCurrentInputToFile(exactCrashFile));
                    }

                    // libFuzzerCompat stats are only displayed when they hit new coverage or crashes
                    if (LIBFUZZER_COMPAT_OUTPUT) {
                        displayStats();
                    }
                }
            }

            // displaying stats on every interval is only enabled for AFL-like stats screen
            if (!LIBFUZZER_COMPAT_OUTPUT) {
                displayStats();
            }

            // Save input unconditionally if such a setting is enabled
            if (LOG_ALL_INPUTS && (SAVE_ONLY_VALID ? valid : true)) {
                File logDirectory = new File(allInputsDirectory, result.toString().toLowerCase());
                String saveFileName = String.format("id_%09d", numTrials);
                File saveFile = new File(logDirectory, saveFileName);
                GuidanceException.wrap(() -> writeCurrentInputToFile(saveFile));
            }
        }

    /**
     * collect coverage results
     */
    public Consumer<TraceEvent> generateCallBack(Thread thread) {
        return (event) -> {
            //@TODO
            runCoverage.handleEvent(event);
        };
    }
}
