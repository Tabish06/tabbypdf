package ru.cells.icc.utils;

import com.itextpdf.awt.geom.Rectangle;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.parser.*;
import ru.cells.icc.common.TextChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by sunveil on 27/06/16.
 */
public class MikhailovTextExtractionStrategy implements TextExtractionStrategy {

    static boolean DUMP_STATE = false;

    private final ArrayList<TextChunk> locationalResult = new ArrayList<TextChunk>();

    private final ArrayList<TextChunk> locationalChunkResult = new ArrayList<TextChunk>();

    private final ArrayList<TextChunk> locationalJChunkResult = new ArrayList<TextChunk>();

    private final ArrayList<TextChunk> locationalWordResult = new ArrayList<TextChunk>();

    private final ArrayList<Rectangle> locationalTextBlocks = new ArrayList<Rectangle>();

    private ArrayList<Line> lines = null;

    private int   rotation = 0;

    private float width    = 0;
    public MikhailovTextExtractionStrategy(int rotation, float width) {
        this.rotation = rotation;
        this.width = width;
    }

    public ArrayList<TextChunk> getLocationalResult() {
        return locationalResult;
    }

    public ArrayList<TextChunk> getLocationalChunkResult() {
        return locationalChunkResult;
    }

    @Override
    public void beginTextBlock() {
    }

    @Override
    public void endTextBlock() {
    }


    /**
     * Returns the result so far.
     *
     * @return a String with the resulting text.
     */
    @Override
    public String getResultantText() {
        return getResultantText(null);
    }

    @Override
    public void renderImage(ImageRenderInfo renderInfo) {
        // do nothing
    }

    @Override
    public void renderText(TextRenderInfo renderInfo) {
        renderInfo.getCharacterRenderInfos()
                  .stream()//extract chunks per each character
                  .filter(rI -> !rI.getText().isEmpty())
                  .forEachOrdered(rI -> locationalResult.add(extractLocation(rI)));

        locationalChunkResult.add(extractLocation(renderInfo)); //extract chunk
    }

    private TextChunk extractLocation(TextRenderInfo renderInfo) {
        LineSegment topSegment = renderInfo.getAscentLine();
        LineSegment btmSegment = renderInfo.getDescentLine();
        if (renderInfo.getRise() !=
            0) { // remove the rise from the baseline - we do this because the text from a super/subscript render operations should probably be considered as part of the baseline of the text the super/sub is relative to
            Matrix riseOffsetTransform = new Matrix(0, -renderInfo.getRise());
            btmSegment = btmSegment.transformBy(riseOffsetTransform);
            topSegment = topSegment.transformBy(riseOffsetTransform);
        }
        TextChunk location;
        Vector    startLocation, endLocation, rightTopPoint;
        if (rotation == 90) {  //transform chunk coordinates according to rotation angle
            startLocation = new Vector(btmSegment.getStartPoint().get(1), width - btmSegment.getStartPoint().get(0), 0);
            endLocation = new Vector(btmSegment.getEndPoint().get(1), width - btmSegment.getEndPoint().get(0), 0);
            rightTopPoint = new Vector(topSegment.getEndPoint().get(1), width - topSegment.getEndPoint().get(0), 0);
        } else {
            startLocation = btmSegment.getStartPoint();
            endLocation = btmSegment.getEndPoint();
            rightTopPoint = topSegment.getEndPoint();
        }
        location = new TextChunk(renderInfo.getText(), startLocation, endLocation, renderInfo.getSingleSpaceWidth());
        location.setRightTopPoint(rightTopPoint);
        GraphicsState gs   = ReflectionIText.getGs(renderInfo);
        Font          font = new Font(gs.getFont(), gs.getFontSize(), gs.getFont().getFontType(), gs.getFillColor());
        location.setFont(font);
        return location;
    }

    public interface TextChunkFilter {
        public boolean accept(TextChunk textChunk);
    }

    private boolean startsWithSpace(String str) {
        return !str.isEmpty() && str.charAt(0) == ' ';
    }

    private boolean endsWithSpace(String str) {
        return !str.isEmpty() && str.charAt(str.length() - 1) == ' ';
    }

    private boolean isEmptyChunk(String str) {
        return str.isEmpty() || str.charAt(str.length() - 1) == ' ';
    }

    private List<TextChunk> filterTextChunks(List<TextChunk> textChunks, TextChunkFilter filter) {
        if (filter == null) return textChunks;
        return textChunks.stream().filter(filter::accept).collect(Collectors.toList());
    }

    private boolean isChunkAtWordBoundary(TextChunk chunk, TextChunk previousChunk) {
        if (chunk.getCharSpaceWidth() < 0.1f) return false;
        float dist = chunk.distanceFromEndOf(previousChunk);
        return dist < -chunk.getCharSpaceWidth() || dist > chunk.getCharSpaceWidth() / 2.0f;

    }

    protected boolean isChunkAtSpace(TextChunk chunk, TextChunk previousChunk) {
        if (chunk == previousChunk) return false;
        float dist = chunk.distanceFromEndOf(previousChunk);
        float sp   = chunk.getCharSpaceWidth();
        if (sp > 56) {// TODO: 15.09.2016 What is that magic constant? also below
            sp = sp / 5.0f;
        } else if (sp > 14) {
            sp += 1;
        } else if (sp > 5) {
            sp += 0.1;
        } else {
            sp = 5;
        }
        return Math.abs(dist) > sp;
    }

    protected boolean isDiffFonts(TextChunk chunk, TextChunk previousChunk) {
        return false;
    }

    public String getResultantText(TextChunkFilter chunkFilter) {
        if (DUMP_STATE) dumpState();
        List<TextChunk> filteredTextChunks = filterTextChunks(locationalResult, chunkFilter);
        Collections.sort(filteredTextChunks);

        StringBuilder sb        = new StringBuilder();
        TextChunk     lastChunk = null;
        for (TextChunk chunk : filteredTextChunks) {
            if (lastChunk == null) {
                sb.append(chunk.getText());
            } else {
                if (chunk.sameLine(lastChunk)) {
                    if (isChunkAtWordBoundary(chunk, lastChunk) && !startsWithSpace(chunk.getText()) &&
                        !endsWithSpace(lastChunk.getText())) sb.append(' ');
                    sb.append(chunk.getText());
                } else {
                    sb.append('\n');
                    sb.append(chunk.getText());
                }
            }
            lastChunk = chunk;
        }
        return sb.toString();
    }

    public ArrayList<TextChunk> getResultantWordLocation(ArrayList<Line> lines) {
        this.lines = lines;
        return getResultantWordLocation((TextChunkFilter) null);
    }

    public boolean clearChunk(TextChunk c) {
        for (int i = 0; i < c.getText().length(); i++) {
            if (c.getText().toCharArray()[i] != '-') {
                return true;
            }
        }
        return false;
    }

    public boolean vSplit(TextChunk t1, TextChunk t2) {
        if (rotation != 0) return false;
        if (lines != null) for (Line l : lines) {
            float x1   = (float) l.getBasePoints().get(0).getX();
            float x2   = (float) l.getBasePoints().get(1).getX();
            float t1x  = t1.getEndLocation().get(0);
            float t2x  = t2.getStartLocation().get(0);
            float t1y1 = t1.getRightTopPoint().get(1);
            float t1y2 = t1.getStartLocation().get(1);
            float y1   = (float) Math.max(l.getBasePoints().get(0).getY(), (float) l.getBasePoints().get(1).getY());
            float y2   = (float) Math.min(l.getBasePoints().get(0).getY(), (float) l.getBasePoints().get(1).getY());
            if (x1 == x2 && (t1x <= x1) && (t2x >= x2) && (Math.min(t1y1, y1) - Math.max(t1y2, y2)) > 0) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<TextChunk> getResultantWordLocation(TextChunkFilter chunkFilter) {

        locationalWordResult.clear();
        if (DUMP_STATE) dumpState();
        List<TextChunk> filteredTextChunks = filterTextChunks(locationalChunkResult, chunkFilter);
        Vector          start              = null;
        Vector          end                = null;
        boolean         isWord             = false;
        StringBuffer    lr                 = new StringBuffer();
        TextChunk       lastChunk          = null;
        int             order              = 0;
        TextChunk       tmpChunk           = null;

        Font f = null;

        for (TextChunk chunk : filteredTextChunks) {

            replaceSpecialChars(chunk);

            if (lastChunk == null) {
                lastChunk = chunk;
                continue;
            }
            if ((!isChunkAtSpace(chunk, lastChunk) || lastChunk.getText().equals("•")) && chunk.sameLine(lastChunk) &&
                !vSplit(lastChunk, chunk)) {
                if (!isWord) {
                    if (!isEmptyChunk(lastChunk.getText())) {
                        start = lastChunk.getStartLocation();
                    } else {
                        lr.append(chunk.getText());
                        lastChunk = chunk;
                        start = chunk.getStartLocation();
                        continue;
                    }
                    if (!chunk.sameLine(lastChunk)) {
                        start = chunk.getStartLocation();
                        lr.append(chunk.getText());
                        lastChunk = chunk;
                        continue;
                    }
                    lr.append(lastChunk.getText());
                    isWord = true;
                } else {
                    lr.append(lastChunk.getText());
                    f = lastChunk.getFont();
                    isWord = true;
                }
                end = chunk.getEndLocation();
            } else {
                if (lr.length() > 0) {
                    lr.append(lastChunk.getText());
                    f = lastChunk.getFont();
                }
                if (isWord) {
                    isWord = false;
                    tmpChunk = new TextChunk(lr.toString(), start, end, chunk.getCharSpaceWidth());
                    tmpChunk.setOrder(order);
                    tmpChunk.setRightTopPoint(lastChunk.getRightTopPoint());
                    tmpChunk.setFont(lastChunk.getFont());
                    locationalWordResult.add(tmpChunk);
                    order++;
                    lr.delete(0, lr.length());
                } else if (!isEmptyChunk(lastChunk.getText())) {
                    isWord = false;
                    tmpChunk =
                            new TextChunk(lastChunk.getText(), lastChunk.getStartLocation(), lastChunk.getEndLocation(),
                                          chunk.getCharSpaceWidth());
                    tmpChunk.setOrder(order);
                    tmpChunk.setRightTopPoint(lastChunk.getRightTopPoint());
                    tmpChunk.setFont(lastChunk.getFont());
                    locationalWordResult.add(tmpChunk);
                    order++;
                }
                if (chunk == filteredTextChunks.get(filteredTextChunks.size() - 1) &&
                    !isEmptyChunk(lastChunk.getText())) {
                    isWord = false;
                    tmpChunk = new TextChunk(chunk.getText(), chunk.getStartLocation(), chunk.getEndLocation(),
                                             chunk.getCharSpaceWidth());
                    tmpChunk.setOrder(order);
                    tmpChunk.setRightTopPoint(chunk.getRightTopPoint());
                    tmpChunk.setFont(lastChunk.getFont());
                    locationalWordResult.add(tmpChunk);
                    order++;
                }
            }
            if (!chunk.equals(" ")) {
                lastChunk = chunk;
            }
        }
        if (isWord) {
            lr.append(lastChunk.getText());
            tmpChunk = new TextChunk(lr.toString(), start, lastChunk.getEndLocation(), lastChunk.getCharSpaceWidth());
            tmpChunk.setRightTopPoint(lastChunk.getRightTopPoint());
            tmpChunk.setOrder(order);
            tmpChunk.setFont(lastChunk.getFont());
            if (clearChunk(tmpChunk)) {
                locationalWordResult.add(tmpChunk);
            }

        }
        return locationalWordResult;
    }

    private void replaceSpecialChars(TextChunk chunk) {
        if (chunk.getText().equals("¦")) {
            chunk.setText("");
        } else if (chunk.getText().equals("&")) {
            chunk.setText("&amp;");
        } else if (chunk.getText().equals("<")) {
            chunk.setText("&lt;");
        } else if (chunk.getText().equals(">")) {
            chunk.setText("&gt;");
        } else if (chunk.getText().equals("\"")) {
            chunk.setText("&quot;");
        } else if (chunk.getText().equals("‘")) {
            chunk.setText("'");
        }
    }

    /**
     * Used for debugging only
     */
    private void dumpState() {
        for (TextChunk location : locationalResult) {
            location.printDiagnostics();
            System.out.println();
        }

    }
}