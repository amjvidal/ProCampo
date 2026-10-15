package com.procampo.controller;

import com.procampo.dao.ModeloDAO;
import com.procampo.db.DatabaseConnection;
import com.procampo.model.Modelo;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller principal da aplicação Desktop (Navegação, Catálogo de Modelos RF01/RF02,
 * Criação/Exclusão RF08 e Portabilidade do Banco de Dados RF10/RF11).
 */
public class MainController {

    @FXML private StackPane contentPane;
    @FXML private VBox viewModelos;
    @FXML private Label lblStatusDb;

    @FXML private TextField txtBuscaModelo;
    @FXML private ComboBox<String> cbCategoriaModelo;
    @FXML private TableView<Modelo> tblModelos;
    @FXML private TableColumn<Modelo, String> colModNome;
    @FXML private TableColumn<Modelo, String> colModCategoria;
    @FXML private TableColumn<Modelo, String> colModCiclo;
    @FXML private TableColumn<Modelo, String> colModProdutividade;
    @FXML private TableColumn<Modelo, String> colModTotal;

    private final ModeloDAO modeloDAO = new ModeloDAO();
    private final ObservableList<Modelo> listaModelos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colModNome.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNome()));
        colModCategoria.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategoria()));
        colModCiclo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCicloProdutivo()));
        colModProdutividade.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProdutividade()));
        // RN01 - O valor total é calculado dinamicamente com base nas etapas e itens
        colModTotal.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.2f", data.getValue().getTotalGeral())));

        tblModelos.setItems(listaModelos);
        tblModelos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Duplo clique na tabela abre o detalhamento do orçamento (RF03)
        tblModelos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && tblModelos.getSelectionModel().getSelectedItem() != null) {
                handleDetalharModelo();
            }
        });

        txtBuscaModelo.textProperty().addListener((obs, oldV, newV) -> handleBuscarModelos());
        cbCategoriaModelo.setOnAction(e -> handleBuscarModelos());

        atualizarStatusDb();
        carregarModelos();
        preencherCategorias();
    }

    public void carregarModelos() {
        try {
            List<Modelo> todos = modeloDAO.listarTodos();
            listaModelos.setAll(todos);
            preencherCategorias();
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao carregar catálogo de modelos: " + e.getMessage());
        }
    }

    private void preencherCategorias() {
        try {
            List<String> categorias = modeloDAO.listarTodos().stream()
                    .map(Modelo::getCategoria)
                    .filter(c -> c != null && !c.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            categorias.add(0, "Todas");
            String sel = cbCategoriaModelo.getValue();
            cbCategoriaModelo.setItems(FXCollections.observableArrayList(categorias));
            if (sel != null && categorias.contains(sel)) {
                cbCategoriaModelo.setValue(sel);
            } else {
                cbCategoriaModelo.setValue("Todas");
            }
        } catch (SQLException ignored) {}
    }

    @FXML
    private void handleBuscarModelos() {
        try {
            String busca = txtBuscaModelo.getText();
            String cat = cbCategoriaModelo.getValue();
            listaModelos.setAll(modeloDAO.buscarPorNomeECategoria(busca, cat));
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro na Busca", e.getMessage());
        }
    }

    @FXML
    private void handleLimparFiltrosModelo() {
        txtBuscaModelo.clear();
        cbCategoriaModelo.setValue("Todas");
        carregarModelos();
    }

    @FXML
    private void handleNovoModelo() {
        TextInputDialog dialog = new TextInputDialog("Novo Projeto Agropecuário");
        dialog.setTitle("Criação de Modelo");
        dialog.setHeaderText("Digite o nome do novo modelo de orçamento agropecuário:");
        dialog.setContentText("Nome do Projeto:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(nome -> {
            if (!nome.trim().isEmpty()) {
                Modelo novo = new Modelo(nome.trim(), "Anual", "-", "Capacidade Padrão", "Geral");
                try {
                    if (modeloDAO.salvarModelo(novo)) {
                        carregarModelos();
                        // Seleciona e abre automaticamente a tela de detalhamento
                        tblModelos.getSelectionModel().select(novo);
                        handleDetalharModelo();
                    }
                } catch (SQLException e) {
                    mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível criar o modelo. Verifique se já existe projeto com este nome: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleExcluirModelo() {
        Modelo sel = tblModelos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Nenhum Modelo Selecionado", "Selecione um projeto/modelo na tabela para excluir.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Tem certeza que deseja excluir o modelo '" + sel.getNome() + "' e todas as suas etapas e composições associadas?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmação de Exclusão");
        confirm.setHeaderText(null);

        if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                if (modeloDAO.excluirModelo(sel.getIdModelo())) {
                    carregarModelos();
                }
            } catch (SQLException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Falha ao excluir o modelo: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleDetalharModelo() {
        Modelo sel = tblModelos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Atenção", "Selecione um modelo na tabela para visualizar ou editar suas etapas.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/modelo_detail.fxml"));
            Parent view = loader.load();
            ModeloDetailController ctrl = loader.getController();
            ctrl.setModelo(sel, this);

            contentPane.getChildren().setAll(view);
        } catch (IOException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro de Navegação", "Falha ao carregar tela de detalhamento do orçamento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void showModelosView() {
        contentPane.getChildren().setAll(viewModelos);
        carregarModelos();
    }

    @FXML
    public void showItensView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/item_view.fxml"));
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
        } catch (IOException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro de Navegação", "Falha ao carregar Catálogo de Preços: " + e.getMessage());
        }
    }

    /**
     * RF10 - Exportar Banco de Dados (.db / .sqlite)
     */
    @FXML
    private void handleExportDb() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportar Banco de Dados SQLite");
        fileChooser.setInitialFileName("orcamentos_exportado.db");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Banco SQLite (*.db, *.sqlite)", "*.db", "*.sqlite"),
                new FileChooser.ExtensionFilter("Todos os Arquivos (*.*)", "*.*")
        );

        File dest = fileChooser.showSaveDialog(contentPane.getScene().getWindow());
        if (dest != null) {
            try {
                if (DatabaseConnection.getInstance().exportDatabase(dest)) {
                    mostrarAlerta(Alert.AlertType.INFORMATION, "Exportado com Sucesso",
                            "O arquivo completo do banco de dados portátil foi salvo em:\n" + dest.getAbsolutePath());
                }
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro na Exportação", "Falha ao salvar cópia do banco: " + e.getMessage());
            }
        }
    }

    /**
     * RF11 - Importar Banco de Dados (.db / .sqlite)
     */
    @FXML
    private void handleImportDb() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importar Banco de Dados SQLite Externo");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Banco SQLite (*.db, *.sqlite)", "*.db", "*.sqlite"),
                new FileChooser.ExtensionFilter("Todos os Arquivos (*.*)", "*.*")
        );

        File source = fileChooser.showOpenDialog(contentPane.getScene().getWindow());
        if (source != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Importar um banco de dados externo substituirá a base atual de preços e orçamentos pelos dados do arquivo selecionado.\nDeseja continuar?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirmação de Importação de Banco");
            confirm.setHeaderText("Atenção: Os dados atuais serão substituídos");

            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                try {
                    if (DatabaseConnection.getInstance().importDatabase(source)) {
                        // RE-INITIALIZE to apply missing columns/migrations to the imported DB
                        com.procampo.db.DatabaseInitializer.initialize();
                        
                        mostrarAlerta(Alert.AlertType.INFORMATION, "Importado com Sucesso",
                                "O banco de dados foi substituído pelo arquivo externo!\nOs novos preços e modelos já estão disponíveis.");
                        showModelosView();
                    }
                } catch (IOException e) {
                    mostrarAlerta(Alert.AlertType.ERROR, "Erro na Importação", "Falha ao carregar banco de dados externo: " + e.getMessage());
                }
            }
        }
    }

    private void atualizarStatusDb() {
        File f = DatabaseConnection.getCurrentDbPath();
        if (f != null) {
            lblStatusDb.setText("🟢 Banco Local: " + f.getName());
        }
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String msg) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
