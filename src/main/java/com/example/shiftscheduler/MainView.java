package com.example.shiftscheduler;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@PermitAll
@Route("")
public class MainView extends VerticalLayout {

    // Lock the start date to the Monday of the current week
    private LocalDate viewStartDate = LocalDate.now().with(DayOfWeek.MONDAY);
    
    private final VerticalLayout gridContainer = new VerticalLayout();
    private final ShiftRepository shiftRepo;
    private final EmployeeRepository empRepo;
    
    private final boolean isManager;
    private final String loggedInUsername;

    public MainView(ShiftRepository shiftRepo, EmployeeRepository empRepo) {
        this.shiftRepo = shiftRepo;
        this.empRepo = empRepo;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        this.isManager = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        this.loggedInUsername = auth.getName(); 

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H1("Dienstplan"));
        header.add(new RouterLink("➡️ Zur Tauschbörse", SwapBoardView.class));
        add(header);

        if (isManager) {
            buildAddShiftForm();
        }

        gridContainer.setWidthFull();
        add(gridContainer);
        buildMatrixGrid();
    }

    private void buildAddShiftForm() {
        // 1. Standard Shift Buttons 
        ComboBox<Employee> employeeSelect = new ComboBox<>("Mitarbeiter");
        employeeSelect.setItems(empRepo.findAll());
        employeeSelect.setItemLabelGenerator(Employee::getName);

        DatePicker dateSelect = new DatePicker("Datum");
        dateSelect.setValue(LocalDate.now());

        HorizontalLayout shiftButtons1 = new HorizontalLayout();
        HorizontalLayout shiftButtons2 = new HorizontalLayout();

        addShiftButton(shiftButtons1, "S1", "08:00", "16:30", employeeSelect, dateSelect);
        addShiftButton(shiftButtons1, "s1", "11:30", "20:00", employeeSelect, dateSelect);
        addShiftButton(shiftButtons1, "S2", "07:30", "16:00", employeeSelect, dateSelect);
        addShiftButton(shiftButtons1, "s5", "16:00", "20:00", employeeSelect, dateSelect);
        addShiftButton(shiftButtons1, "S5", "07:30", "14:00", employeeSelect, dateSelect);
        
        addShiftButton(shiftButtons2, "S3", "07:30", "10:30", employeeSelect, dateSelect);
        addShiftButton(shiftButtons2, "s4", "11:00", "16:30", employeeSelect, dateSelect);
        addShiftButton(shiftButtons2, "s3", "14:30", "20:00", employeeSelect, dateSelect);
        addShiftButton(shiftButtons2, "s2", "17:00", "20:00", employeeSelect, dateSelect);
        addShiftButton(shiftButtons2, "S4", "11:00", "14:30", employeeSelect, dateSelect);

        add(new H3("Schicht hinzufügen (Nur Boss):"), new HorizontalLayout(employeeSelect, dateSelect), shiftButtons1, shiftButtons2);

        // 2. Urlaub / Krank 
        H3 ukTitle = new H3("Urlaub / Krankheit eintragen (Zeitraum):");
        ComboBox<Employee> ukEmpSelect = new ComboBox<>("Mitarbeiter");
        ukEmpSelect.setItems(empRepo.findAll());
        ukEmpSelect.setItemLabelGenerator(Employee::getName);

        DatePicker startDateSelect = new DatePicker("Von (Datum)");
        DatePicker endDateSelect = new DatePicker("Bis (Datum)");
        ComboBox<String> typeSelect = new ComboBox<>("Typ");
        typeSelect.setItems("U", "K");

        Button addUkBtn = new Button("Eintragen", e -> {
            if (ukEmpSelect.getValue() == null || startDateSelect.getValue() == null || endDateSelect.getValue() == null || typeSelect.getValue() == null) {
                Notification.show("Bitte alle Felder ausfüllen!");
                return;
            }
            LocalDate start = startDateSelect.getValue();
            LocalDate end = endDateSelect.getValue();
            
            if (end.isBefore(start)) {
                Notification.show("Das Enddatum darf nicht vor dem Startdatum liegen!").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Loop through the dates and add U/K
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                // Find and delete any existing shifts for this person on this day
                LocalDate finalD = d;
                List<Shift> existingShifts = shiftRepo.findAll().stream()
                        .filter(s -> s.getEmployee().getId().equals(ukEmpSelect.getValue().getId()))
                        .filter(s -> s.getStartTime().toLocalDate().equals(finalD))
                        .collect(Collectors.toList());
                shiftRepo.deleteAll(existingShifts);

                // Create the U/K block
                Shift s = new Shift();
                s.setLabel(typeSelect.getValue());
                s.setEmployee(ukEmpSelect.getValue());
                s.setStartTime(d.atTime(0, 0)); // Start of day
                s.setEndTime(d.atTime(23, 59)); // End of day
                shiftRepo.save(s);
            }
            buildMatrixGrid(); // Refresh grid
            Notification.show("Zeitraum erfolgreich eingetragen!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        addUkBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        add(ukTitle, new HorizontalLayout(ukEmpSelect, startDateSelect, endDateSelect, typeSelect, addUkBtn));
    }

    private void buildMatrixGrid() {
        gridContainer.removeAll();

        // Format the Week Label
        LocalDate endOfWeek = viewStartDate.plusDays(6);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        H3 weekLabel = new H3("Woche: " + viewStartDate.format(fmt) + " - " + endOfWeek.format(fmt));

        HorizontalLayout weekControls = new HorizontalLayout();
        weekControls.setAlignItems(Alignment.CENTER);
        Button prevWeek = new Button("⬅️ Vorherige Woche", e -> { viewStartDate = viewStartDate.minusDays(7); buildMatrixGrid(); });
        Button nextWeek = new Button("Nächste Woche ➡️", e -> { viewStartDate = viewStartDate.plusDays(7); buildMatrixGrid(); });
        weekControls.add(prevWeek, weekLabel, nextWeek);

        Grid<Employee> grid = new Grid<>(Employee.class, false);
        grid.addColumn(Employee::getName).setHeader("Mitarbeiter").setFrozen(true).setAutoWidth(true);

        List<Shift> allShifts = shiftRepo.findAll();
        String[] dayNames = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = viewStartDate.plusDays(i);
            String headerDate = dayNames[i] + " " + currentDate.format(fmt);

            grid.addComponentColumn(emp -> {
                Optional<Shift> shiftOpt = allShifts.stream()
                        .filter(s -> s.getEmployee().getId().equals(emp.getId()))
                        .filter(s -> s.getStartTime().toLocalDate().equals(currentDate))
                        .findFirst();

                if (shiftOpt.isPresent()) {
                    Shift shift = shiftOpt.get();
                    Button shiftBtn = new Button(shift.getLabel());
                    
                    // Visual styling for U and K
                    if (shift.getLabel().equals("U") || shift.getLabel().equals("K")) {
                        shiftBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
                    } else if (shift.isTradeOpen()) {
                        shiftBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
                    } else {
                        shiftBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_PRIMARY);
                    }

                    shiftBtn.addClickListener(e -> openShiftActionsDialog(shift));
                    return shiftBtn;
                } else {
                    return new Span("-");
                }
            }).setHeader(headerDate).setTextAlign(ColumnTextAlign.CENTER).setAutoWidth(true);
        }

        grid.setItems(empRepo.findAll());
        gridContainer.add(weekControls, grid);
    }

    private void openShiftActionsDialog(Shift shift) {
        Dialog dialog = new Dialog();
        
        String searchName = loggedInUsername.replace("emp", "employee ");
        boolean ownsShift = shift.getEmployee().getName().toLowerCase().contains(loggedInUsername) ||
                            shift.getEmployee().getName().toLowerCase().contains(searchName);

        if (!isManager && !ownsShift) {
            dialog.add(new Span("Sie haben keine Berechtigung für diese Schicht."));
            dialog.open();
            return;
        }

        dialog.setHeaderTitle("Optionen für " + shift.getLabel());
        VerticalLayout dialogLayout = new VerticalLayout();

        // 1. Trade Board Controls
        if (ownsShift) {
            if (shift.isTradeOpen()) {
                Button cancelTradeBtn = new Button("Vom Tauschbrett entfernen", e -> {
                    shift.setTradeOpen(false);
                    shift.setTradeNotes("");
                    shift.setOfferedShiftIds(null);
                    shift.setOfferedByUsername(null);
                    shiftRepo.save(shift);
                    getUI().ifPresent(ui -> ui.getPage().reload());
                });
                cancelTradeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
                dialogLayout.add(cancelTradeBtn);
            } else {
                TextField notesField = new TextField("Bedingung für Tausch");
                Button tradeBtn = new Button("🤝 Auf Tauschbörse", e -> {
                    shift.setTradeOpen(true);
                    shift.setTradeNotes(notesField.getValue());
                    shiftRepo.save(shift);
                    getUI().ifPresent(ui -> ui.getPage().reload());
                });
                dialogLayout.add(new HorizontalLayout(notesField, tradeBtn));
            }
        }

        // 2. Manager Only Actions
        if (isManager) {
            ComboBox<Employee> newEmpSelect = new ComboBox<>("Mitarbeiter ändern");
            newEmpSelect.setItems(empRepo.findAll());
            newEmpSelect.setItemLabelGenerator(Employee::getName);

            Button reassignBtn = new Button("🔄 Übergeben", e -> {
                if(newEmpSelect.getValue() != null) {
                    shift.setEmployee(newEmpSelect.getValue());
                    shift.setTradeOpen(false);
                    shiftRepo.save(shift);
                    getUI().ifPresent(ui -> ui.getPage().reload());
                }
            });

            Button deleteBtn = new Button("🗑️ Löschen", e -> {
                shiftRepo.delete(shift);
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

            dialogLayout.add(new HorizontalLayout(newEmpSelect, reassignBtn), deleteBtn);
        }

        dialog.add(dialogLayout);
        dialog.open();
    }

    private void addShiftButton(HorizontalLayout layout, String label, String start, String end, ComboBox<Employee> empSel, DatePicker dateSel) {
        Button btn = new Button(label, e -> {
            if (empSel.getValue() == null || dateSel.getValue() == null) return;
            LocalDateTime sTime = dateSel.getValue().atTime(LocalTime.parse(start));
            LocalDateTime eTime = dateSel.getValue().atTime(LocalTime.parse(end));
            
            if (shiftRepo.countOverlappingShifts(empSel.getValue(), sTime, eTime) == 0) {
                Shift s = new Shift();
                s.setLabel(label);
                s.setEmployee(empSel.getValue());
                s.setStartTime(sTime);
                s.setEndTime(eTime);
                shiftRepo.save(s);
                buildMatrixGrid(); 
            } else {
                Notification n = Notification.show("Mitarbeiter hat bereits eine Schicht!");
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        layout.add(btn);
    }
}