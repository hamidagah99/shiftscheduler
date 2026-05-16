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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@PermitAll
@Route("")
public class MainView extends VerticalLayout {

    private LocalDate viewStartDate = LocalDate.now();
    private final VerticalLayout gridContainer = new VerticalLayout();
    private final ShiftRepository shiftRepo;
    private final EmployeeRepository empRepo;
    
    private final boolean isManager;
    private final String loggedInUsername;

    public MainView(ShiftRepository shiftRepo, EmployeeRepository empRepo) {
        this.shiftRepo = shiftRepo;
        this.empRepo = empRepo;

        // Get user permissions
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        this.isManager = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
        this.loggedInUsername = auth.getName(); // e.g., "boss", "emp1"

        // 1. Header
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H1("Dienstplan"));
        header.add(new RouterLink("➡️ Zur Tauschbörse", SwapBoardView.class));
        add(header);

        // 2. Add Shift Form (ONLY VISIBLE TO MANAGER)
        if (isManager) {
            buildAddShiftForm();
        }

        // 3. Grid Container
        gridContainer.setWidthFull();
        add(gridContainer);
        buildMatrixGrid();
    }

    private void buildAddShiftForm() {
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
    }

    private void buildMatrixGrid() {
        gridContainer.removeAll();

        // Pagination Controls
        HorizontalLayout weekControls = new HorizontalLayout();
        weekControls.setAlignItems(Alignment.CENTER);
        Button prevWeek = new Button("⬅️ Vorherige Woche", e -> { viewStartDate = viewStartDate.minusDays(7); buildMatrixGrid(); });
        Button nextWeek = new Button("Nächste Woche ➡️", e -> { viewStartDate = viewStartDate.plusDays(7); buildMatrixGrid(); });
        H3 weekLabel = new H3("Woche: " + viewStartDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        weekControls.add(prevWeek, weekLabel, nextWeek);

        Grid<Employee> grid = new Grid<>(Employee.class, false);
        grid.addColumn(Employee::getName).setHeader("Mitarbeiter").setFrozen(true).setAutoWidth(true);

        List<Shift> allShifts = shiftRepo.findAll();

        // Build 7 columns dynamically
        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = viewStartDate.plusDays(i);
            String headerDate = currentDate.format(DateTimeFormatter.ofPattern("dd.MM"));

            grid.addComponentColumn(emp -> {
                Optional<Shift> shiftOpt = allShifts.stream()
                        .filter(s -> s.getEmployee().getId().equals(emp.getId()))
                        .filter(s -> s.getStartTime().toLocalDate().equals(currentDate))
                        .findFirst();

                if (shiftOpt.isPresent()) {
                    Shift shift = shiftOpt.get();
                    Button shiftBtn = new Button(shift.getLabel());
                    if (shift.isTradeOpen()) {
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
        
        boolean ownsShift = shift.getEmployee().getName().toLowerCase().replace(" ", "").contains(loggedInUsername);

        if (!isManager && !ownsShift) {
            dialog.add(new Span("Sie haben keine Berechtigung für diese Schicht."));
            dialog.open();
            return;
        }

        VerticalLayout dialogLayout = new VerticalLayout();

        //  Check if there is an incoming swap offer
        if (ownsShift && shift.getOfferedShiftId() != null) {
            Shift offeredShift = shiftRepo.findById(shift.getOfferedShiftId()).orElse(null);
            if (offeredShift != null) {
                dialog.setHeaderTitle("Tauschangebot erhalten!");
                dialogLayout.add(new Span("Benutzer '" + shift.getOfferedByUsername() + "' bietet dir folgende Schicht an:"));
                dialogLayout.add(new H3(offeredShift.getLabel() + " am " + offeredShift.getStartTime().toLocalDate()));
                
                Button acceptBtn = new Button("Tausch Bestätigen", e -> {
                    // Swap the employees
                    Employee myEmp = shift.getEmployee();
                    Employee theirEmp = offeredShift.getEmployee();
                    
                    shift.setEmployee(theirEmp);
                    offeredShift.setEmployee(myEmp);
                    
                    // Clear all trade flags
                    shift.setTradeOpen(false);
                    shift.setTradeNotes("");
                    shift.setOfferedShiftId(null);
                    shift.setOfferedByUsername(null);
                    
                    offeredShift.setTradeOpen(false);
                    offeredShift.setTradeNotes("");
                    offeredShift.setOfferedShiftId(null);
                    offeredShift.setOfferedByUsername(null);
                    
                    shiftRepo.save(shift);
                    shiftRepo.save(offeredShift);
                    
                    dialog.close();
                    getUI().ifPresent(ui -> ui.getPage().reload());
                });
                acceptBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
                
                Button rejectBtn = new Button("Ablehnen", e -> {
                    shift.setOfferedShiftId(null);
                    shift.setOfferedByUsername(null);
                    shiftRepo.save(shift);
                    dialog.close();
                    getUI().ifPresent(ui -> ui.getPage().reload());
                });
                rejectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
                
                dialogLayout.add(new HorizontalLayout(acceptBtn, rejectBtn));
                dialog.add(dialogLayout);
                dialog.open();
                return; // Stop here so they must address the offer
            }
        }

        // Standard Actions (if no offer is pending)
        dialog.setHeaderTitle("Optionen für " + shift.getLabel());

        if (ownsShift) {
            TextField notesField = new TextField("Bedingung für Tausch");
            Button tradeBtn = new Button("🤝 Auf Tauschbörse", e -> {
                shift.setTradeOpen(true);
                shift.setTradeNotes(notesField.getValue());
                shiftRepo.save(shift);
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            dialogLayout.add(new HorizontalLayout(notesField, tradeBtn));
        }

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

            Button deleteBtn = new Button("🗑️ Schicht Löschen", e -> {
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
                buildMatrixGrid(); // Refresh grid instantly without full page reload
            } else {
                Notification n = Notification.show("Mitarbeiter hat bereits eine Schicht!");
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        layout.add(btn);
    }
}