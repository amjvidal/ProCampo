package com.procampo.db;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Importa os dados iniciais (Seed) diretamente da planilha de documentação do BNB/CENOP
 * caso o banco de dados seja inicializado vazio.
 */
public class DataSeedImporter {

    public static void importSeedData(Connection conn) {
        File xlsmFile = new File("Documentaçao", "ORÇAMENTOS CENOP BNB_atualizado.xlsm");
        if (!xlsmFile.exists()) {
            xlsmFile = new File("..", "Documentaçao/ORÇAMENTOS CENOP BNB_atualizado.xlsm");
        }

        if (xlsmFile.exists()) {
            try {
                System.out.println("Lendo arquivo Excel: " + xlsmFile.getAbsolutePath());
                importFromXlsm(conn, xlsmFile);
                return;
            } catch (Exception e) {
                System.err.println("Aviso: Falha na importação via Excel (" + e.getMessage() + "). Usando importação embutida de segurança.");
            }
        } else {
            System.out.println("Arquivo Excel não localizado no diretório padrão. Carregando seed padrão embutida.");
        }

        importFallbackSeed(conn);
    }

    private static void importFromXlsm(Connection conn, File file) throws Exception {
        conn.setAutoCommit(false);
        Map<String, Integer> itemMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            // 1. Importar Catálogo de Itens da aba 'Dados'
            Sheet wsDados = wb.getSheet("Dados");
            if (wsDados != null) {
                String insertItemSql = "INSERT OR IGNORE INTO Item (Descricao, Unidade_Medida, Valor_Unitario) VALUES (?, ?, ?)";
                try (PreparedStatement psItem = conn.prepareStatement(insertItemSql, Statement.RETURN_GENERATED_KEYS)) {
                    for (Row row : wsDados) {
                        if (row.getRowNum() < 11) continue; // Pula cabeçalhos até a linha 12
                        Cell cellDesc = row.getCell(0);
                        Cell cellUnit = row.getCell(1);
                        Cell cellPrice = row.getCell(2);

                        if (cellDesc == null || cellUnit == null || cellPrice == null) continue;

                        String desc = getCellStringValue(cellDesc).trim();
                        String unit = getCellStringValue(cellUnit).trim();
                        double price = getCellDoubleValue(cellPrice);

                        if (desc.isEmpty() || unit.isEmpty() || price <= 0) continue;
                        if (desc.endsWith(":") || desc.equalsIgnoreCase("DISCRIMINAÇÃO")) continue;

                        psItem.setString(1, desc);
                        psItem.setString(2, unit);
                        psItem.setDouble(3, price);
                        psItem.executeUpdate();

                        try (ResultSet rs = psItem.getGeneratedKeys()) {
                            if (rs.next()) {
                                itemMap.put(desc.toUpperCase(), rs.getInt(1));
                            } else {
                                // Se já existia, buscar ID
                                Integer existingId = getItemIdByDesc(conn, desc);
                                if (existingId != null) itemMap.put(desc.toUpperCase(), existingId);
                            }
                        }
                    }
                }
            }

            // 2. Importar Modelos e Composições de todas as abas de orçamento
            String insertModeloSql = "INSERT OR IGNORE INTO Modelo (Nome, Categoria, Ciclo_Produtivo, Espacamento, Produtividade) VALUES (?, ?, ?, ?, ?)";
            String insertEtapaSql = "INSERT INTO Etapa (ID_Modelo, Nome_Etapa, Ordem_Exibicao) VALUES (?, ?, ?)";
            String insertCompSql = "INSERT INTO Composicao (ID_Etapa, ID_Item, Quantidade) VALUES (?, ?, ?)";

            try (PreparedStatement psMod = conn.prepareStatement(insertModeloSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psEtapa = conn.prepareStatement(insertEtapaSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psComp = conn.prepareStatement(insertCompSql)) {

                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet ws = wb.getSheetAt(i);
                    String sName = ws.getSheetName().trim();
                    if (sName.equalsIgnoreCase("Início") || sName.equalsIgnoreCase("Dados") || sName.equalsIgnoreCase("Consid")) {
                        continue;
                    }

                    // Nome do modelo na linha 5 (ex: APRISCO P/ 50 ANIMAIS) e descrição/área na coluna E
                    Row rowHeader = ws.getRow(4);
                    String modelName = sName;
                    String modelArea = "";
                    if (rowHeader != null && rowHeader.getCell(0) != null) {
                        String txt = getCellStringValue(rowHeader.getCell(0)).trim();
                        if (!txt.isEmpty()) modelName = txt;
                        if (rowHeader.getCell(4) != null) {
                            modelArea = getCellStringValue(rowHeader.getCell(4)).trim();
                        }
                    }

                    psMod.setString(1, modelName);
                    psMod.setString(2, "Construções e Instalações");
                    psMod.setString(3, "Perene / Estrutural");
                    psMod.setString(4, "-");
                    psMod.setString(5, modelArea.isEmpty() ? "Capacidade Estrutural" : modelArea);
                    psMod.executeUpdate();

                    int idModelo = -1;
                    try (ResultSet rsMod = psMod.getGeneratedKeys()) {
                        if (rsMod.next() && rsMod.getInt(1) > 0) {
                            idModelo = rsMod.getInt(1);
                        } else {
                            Integer extId = getModeloIdByName(conn, modelName);
                            if (extId != null && extId > 0) idModelo = extId;
                        }
                    }
                    if (idModelo <= 0) {
                        Integer extId = getModeloIdByName(conn, modelName);
                        if (extId != null && extId > 0) idModelo = extId;
                    }
                    if (idModelo <= 0) {
                        System.err.println("Aviso: Falha ao obter ID do modelo '" + modelName + "'. Pulando abas.");
                        continue;
                    }

                    int currentIdEtapa = -1;
                    int ordemEtapa = 1;

                    for (int r = 7; r <= ws.getLastRowNum(); r++) {
                        Row row = ws.getRow(r);
                        if (row == null) continue;
                        Cell c0 = row.getCell(0);
                        if (c0 == null) continue;
                        String txt = getCellStringValue(c0).trim();
                        if (txt.isEmpty()) continue;

                        // Verifica se é cabeçalho de Etapa (ex: "1) MATERIAIS", "2) MÃO DE OBRA")
                        if ((txt.matches("^\\d+\\).*") || txt.equalsIgnoreCase("MATERIAIS") || txt.equalsIgnoreCase("MÃO DE OBRA"))
                                && (row.getCell(1) == null || getCellStringValue(row.getCell(1)).trim().isEmpty())) {
                            
                            psEtapa.setInt(1, idModelo);
                            psEtapa.setString(2, txt);
                            psEtapa.setInt(3, ordemEtapa++);
                            psEtapa.executeUpdate();
                            try (ResultSet rsEtapa = psEtapa.getGeneratedKeys()) {
                                if (rsEtapa.next() && rsEtapa.getInt(1) > 0) currentIdEtapa = rsEtapa.getInt(1);
                            }
                            continue;
                        }

                        // Se temos uma etapa ativa e a linha tem quantidade e unidade, é um item da composição
                        if (currentIdEtapa > 0) {
                            Cell cQtd = row.getCell(1);
                            Cell cUnit = row.getCell(2);
                            Cell cPrice = row.getCell(3);

                            if (cQtd != null && cUnit != null && cPrice != null) {
                                double qtd = getCellDoubleValue(cQtd);
                                String unit = getCellStringValue(cUnit).trim();
                                double price = getCellDoubleValue(cPrice);

                                if (qtd > 0 && price > 0 && !unit.isEmpty()) {
                                    Integer idItem = itemMap.get(txt.toUpperCase());
                                    if (idItem == null || idItem <= 0) {
                                        // Cria item se ainda não estiver no catálogo
                                        idItem = insertOrGetItem(conn, txt, unit, price);
                                        itemMap.put(txt.toUpperCase(), idItem);
                                    }

                                    if (idItem != null && idItem > 0 && currentIdEtapa > 0) {
                                        try {
                                            psComp.setInt(1, currentIdEtapa);
                                            psComp.setInt(2, idItem);
                                            psComp.setDouble(3, qtd);
                                            psComp.executeUpdate();
                                        } catch (Exception ex) {
                                            System.err.println("Falha ao vincular composição na etapa " + currentIdEtapa + " com item '" + txt + "' (ID_Item=" + idItem + "): " + ex.getMessage());
                                        }
                                    } else {
                                        System.err.println("Aviso: ID_Item ou ID_Etapa inválido para '" + txt + "' (ID_Item=" + idItem + ", ID_Etapa=" + currentIdEtapa + ")");
                                    }
                                }
                            }
                        }
                    }
                }
            }

            conn.commit();
            System.out.println("Importação da planilha Excel concluída com sucesso: " + itemMap.size() + " itens catalogados.");
        } catch (Exception e) {
            conn.rollback();
            e.printStackTrace();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void importFallbackSeed(Connection conn) {
        try {
            conn.setAutoCommit(false);

            // Itens Core
            int idPedreiro = insertOrGetItem(conn, "PEDREIRO/CARPINTEIRO", "HD", 120.0);
            int idAjudante = insertOrGetItem(conn, "AJUDANTE", "HD", 60.0);
            int idCimento = insertOrGetItem(conn, "CIMENTO", "SC", 50.0);
            int idAreia = insertOrGetItem(conn, "AREIA", "M³", 75.0);
            int idLinhas = insertOrGetItem(conn, "LINHAS 3x4", "M", 22.0);
            int idCaibros = insertOrGetItem(conn, "CAIBROS", "M", 4.5);
            int idRipas = insertOrGetItem(conn, "RIPAS", "M", 4.0);
            int idEstacotes = insertOrGetItem(conn, "ESTACOTES", "UNID", 5.0);

            // Modelo Aprisco 50 Animais
            try (PreparedStatement psM = conn.prepareStatement("INSERT OR IGNORE INTO Modelo (Nome, Categoria, Ciclo_Produtivo, Espacamento, Produtividade) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                psM.setString(1, "APRISCO P/ 50 ANIMAIS");
                psM.setString(2, "Construções e Instalações");
                psM.setString(3, "Perene");
                psM.setString(4, "-");
                psM.setString(5, "ÁREA DE 120 M² SENDO 40 M² COBERTA");
                psM.executeUpdate();

                int idModelo = getModeloIdByName(conn, "APRISCO P/ 50 ANIMAIS");

                // Etapa 1: Materiais
                int idEtapaMat = insertEtapa(conn, idModelo, "1) MATERIAIS", 1);
                insertComposicao(conn, idEtapaMat, idLinhas, 62);
                insertComposicao(conn, idEtapaMat, idCaibros, 190);
                insertComposicao(conn, idEtapaMat, idRipas, 210);
                insertComposicao(conn, idEtapaMat, idCimento, 3);
                insertComposicao(conn, idEtapaMat, idAreia, 2);
                insertComposicao(conn, idEtapaMat, idEstacotes, 1500);

                // Etapa 2: Mão de Obra
                int idEtapaMo = insertEtapa(conn, idModelo, "2) MÃO DE OBRA", 2);
                insertComposicao(conn, idEtapaMo, idPedreiro, 22);
                insertComposicao(conn, idEtapaMo, idAjudante, 15);
            }

            conn.commit();
            System.out.println("Carga inicial padrão embutida finalizada com sucesso.");
        } catch (Exception e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            e.printStackTrace();
        } finally {
            try { conn.setAutoCommit(true); } catch (Exception ignored) {}
        }
    }

    private static String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((int) cell.getNumericCellValue());
        return cell.toString();
    }

    private static double getCellDoubleValue(Cell cell) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
            try { return cell.getNumericCellValue(); } catch (Exception ignored) {}
        }
        try {
            return Double.parseDouble(cell.toString().replace(",", ".").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static int insertOrGetItem(Connection conn, String desc, String unit, double price) throws Exception {
        Integer existing = getItemIdByDesc(conn, desc);
        if (existing != null && existing > 0) return existing;

        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO Item (Descricao, Unidade_Medida, Valor_Unitario) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, desc.trim());
            ps.setString(2, unit.trim());
            ps.setDouble(3, price);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next() && rs.getInt(1) > 0) return rs.getInt(1);
            }
        }
        Integer found = getItemIdByDesc(conn, desc);
        return found != null ? found : 0;
    }

    private static Integer getItemIdByDesc(Connection conn, String desc) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT ID_Item FROM Item WHERE UPPER(Descricao) = UPPER(?)")) {
            ps.setString(1, desc.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID_Item");
            }
        }
        return null;
    }

    private static Integer getModeloIdByName(Connection conn, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT ID_Modelo FROM Modelo WHERE UPPER(Nome) = UPPER(?)")) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID_Modelo");
            }
        }
        return null;
    }

    private static int insertEtapa(Connection conn, int idModelo, String nome, int ordem) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Etapa (ID_Modelo, Nome_Etapa, Ordem_Exibicao) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idModelo);
            ps.setString(2, nome);
            ps.setInt(3, ordem);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    private static void insertComposicao(Connection conn, int idEtapa, int idItem, double qtd) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Composicao (ID_Etapa, ID_Item, Quantidade) VALUES (?, ?, ?)")) {
            ps.setInt(1, idEtapa);
            ps.setInt(2, idItem);
            ps.setDouble(3, qtd);
            ps.executeUpdate();
        }
    }
}
