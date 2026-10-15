package com.procampo.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.procampo.model.Composicao;
import com.procampo.model.Etapa;
import com.procampo.model.Modelo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PdfExportUtil {

    public static void exportModeloToPdf(Modelo modelo, File file) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));

        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

        // Title
        Paragraph title = new Paragraph("Orçamento ProCampo", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));

        // Metadata
        document.add(new Paragraph("Projeto: " + modelo.getNome(), subtitleFont));
        document.add(new Paragraph("Categoria: " + (modelo.getCategoria() != null ? modelo.getCategoria() : "-"), regularFont));
        document.add(new Paragraph("Ciclo Produtivo: " + (modelo.getCicloProdutivo() != null ? modelo.getCicloProdutivo() : "-"), regularFont));
        document.add(new Paragraph("Espaçamento: " + (modelo.getEspacamento() != null ? modelo.getEspacamento() : "-"), regularFont));
        document.add(new Paragraph("Produtividade: " + (modelo.getProdutividade() != null ? modelo.getProdutividade() : "-"), regularFont));
        document.add(new Paragraph(" "));

        // Iterar etapas e itens
        if (modelo.getEtapas() != null && !modelo.getEtapas().isEmpty()) {
            for (Etapa etapa : modelo.getEtapas()) {
                Paragraph etapaTitle = new Paragraph("Etapa: " + etapa.getNomeEtapa(), subtitleFont);
                etapaTitle.setSpacingBefore(10);
                etapaTitle.setSpacingAfter(5);
                document.add(etapaTitle);

                if (etapa.getComposicoes() != null && !etapa.getComposicoes().isEmpty()) {
                    PdfPTable table = new PdfPTable(5);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[]{4f, 1.5f, 1f, 1.5f, 2f});

                    addTableHeader(table, headerFont, "Item", "Categoria", "Unid", "Qtd", "Total (R$)");

                    for (Composicao c : etapa.getComposicoes()) {
                        table.addCell(new Phrase(c.getItem().getDescricao(), regularFont));
                        table.addCell(new Phrase(c.getItem().getCategoria() != null ? c.getItem().getCategoria() : "", regularFont));
                        table.addCell(new Phrase(c.getItem().getUnidadeMedida(), regularFont));
                        table.addCell(new Phrase(String.format("%.2f", c.getQuantidade()), regularFont));
                        table.addCell(new Phrase(String.format("%.2f", c.getSubtotal()), regularFont));
                    }
                    document.add(table);
                } else {
                    document.add(new Paragraph("Nenhum item adicionado a esta etapa.", regularFont));
                }
                
                Paragraph subtotalEtapa = new Paragraph(String.format("Subtotal da Etapa: R$ %.2f", etapa.getTotalEtapa()), headerFont);
                subtotalEtapa.setAlignment(Element.ALIGN_RIGHT);
                subtotalEtapa.setSpacingAfter(10);
                document.add(subtotalEtapa);
            }
        } else {
            document.add(new Paragraph("Este orçamento ainda não possui etapas cadastradas.", regularFont));
        }

        document.add(new Paragraph(" "));
        Paragraph totalGeral = new Paragraph(String.format("TOTAL GERAL: R$ %.2f", modelo.getTotalGeral()), titleFont);
        totalGeral.setAlignment(Element.ALIGN_RIGHT);
        document.add(totalGeral);

        document.close();
    }

    private static void addTableHeader(PdfPTable table, Font font, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(new java.awt.Color(220, 220, 220));
            table.addCell(cell);
        }
    }
}
