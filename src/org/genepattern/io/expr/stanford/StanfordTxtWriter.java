/*
 The Broad Institute
 SOFTWARE COPYRIGHT NOTICE AGREEMENT
 This software and its documentation are copyright (2003-2006) by the
 Broad Institute/Massachusetts Institute of Technology. All rights are
 reserved.

 This software is supplied without any warranty or guaranteed support
 whatsoever. Neither the Broad Institute nor MIT can be responsible for its
 use, misuse, or functionality.
 */

package org.genepattern.io.expr.stanford;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.genepattern.data.expr.ExpressionConstants;
import org.genepattern.data.expr.IExpressionData;
import org.genepattern.io.expr.IExpressionDataWriter;

/**
 * Writes Stanford txt files.
 * 
 * @author Joshua Gould
 */
public class StanfordTxtWriter implements IExpressionDataWriter {
    final static String FORMAT_NAME = "txt";

    public String checkFileExtension(String filename) {
        if (!filename.toLowerCase().endsWith(".txt")) {
            filename += ".txt";
        }
        return filename;
    }

    public void write(IExpressionData expressionData, OutputStream os)
            throws IOException {
        PrintWriter out = new PrintWriter(os);
        int rows = expressionData.getRowCount();
        int columns = expressionData.getColumnCount();
        out.print("ID");
        out.print("\t");
        out.print("NAME");
        for (int j = 0; j < columns; j++) {
            out.print("\t");
            out.print(expressionData.getColumnName(j));
        }
        for (int i = 0; i < rows; i++) {
            out.println();
            out.print(expressionData.getRowName(i));
            out.print("\t");
            String rowDescription = expressionData.getRowMetadata(i,
                    ExpressionConstants.DESC);
            if (rowDescription == null) {
                rowDescription = "";
            }
            out.print(rowDescription);
            for (int j = 0; j < columns; j++) {
                out.print("\t");
                out.print(expressionData.getValueAsString(i, j));
            }
        }
        out.println();
        out.flush();
        out.close();
    }

    public String getFormatName() {
        return FORMAT_NAME;
    }
}
