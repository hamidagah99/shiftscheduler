package com.example.shiftscheduler;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@PermitAll
@Route("")
public class MainView extends VerticalLayout {

    public MainView(ShiftRepository shiftRepo, EmployeeRepository empRepo) {
        add(new H1("Seniorenresidenz Laurentiusplatz - Dienstplan"));

        ComboBox<Employee> employeeSelect = new ComboBox<>("Mitarbeiter");
        employeeSelect.setItems(empRepo.findAll());
        employeeSelect.setItemLabelGenerator(Employee::getName);

        DatePicker dateSelect = new DatePicker("Datum");
        dateSelect.setValue(LocalDate.now());

        HorizontalLayout selectionArea = new HorizontalLayout(employeeSelect, dateSelect);
        add(selectionArea);

        
        HorizontalLayout shiftButtons1 = new HorizontalLayout();
        HorizontalLayout shiftButtons2 = new HorizontalLayout();

        // Row 1
        addShiftButton(shiftButtons1, "S1", "08:00", "16:30", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons1, "s1", "11:30", "20:00", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons1, "S2", "07:30", "16:00", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons1, "s5", "16:00", "20:00", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons1, "S5", "07:30", "14:00", employeeSelect, dateSelect, shiftRepo);
        
        // Row 2
        addShiftButton(shiftButtons2, "S3", "07:30", "10:30", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons2, "s4", "11:00", "16:30", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons2, "s3", "14:30", "20:00", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons2, "s2", "17:00", "20:00", employeeSelect, dateSelect, shiftRepo);
        addShiftButton(shiftButtons2, "S4", "11:00", "14:30", employeeSelect, dateSelect, shiftRepo);

        add(new H3("Schicht auswählen:"), shiftButtons1, shiftButtons2);

        // 3. Main Roster Table
        Grid<Shift> grid = new Grid<>(Shift.class, false);
        grid.addColumn(shift -> shift.getEmployee().getName()).setHeader("Mitarbeiter");
        grid.addColumn(Shift::getLabel).setHeader("Schicht");
        grid.addColumn(shift -> shift.getStartTime().toLocalDate()).setHeader("Datum");
        grid.setItems(shiftRepo.findAll());
        add(new H3("Aktueller Plan"), grid);

        // 4. Reference Table 
        add(new H3("Schicht-Legende"));
        Grid<String[]> legend = new Grid<>();
        legend.addColumn(s -> s[0]).setHeader("Kürzel");
        legend.addColumn(s -> s[1]).setHeader("Zeitraum");
        legend.setItems(List.of(
            new String[]{"S1", "08:00 - 16:30"},
            new String[]{"s1", "11:30 - 20:00"},
            new String[]{"S2", "07:30 - 16:00"},
            new String[]{"s5", "16:00 - 20:00"},
            new String[]{"S5", "07:30 - 14:00"},
            new String[]{"S3", "07:30 - 10:30"},
            new String[]{"s4", "11:00 - 16:30"},
            new String[]{"s3", "14:30 - 20:00"},
            new String[]{"s2", "17:00 - 20:00"},
            new String[]{"S4", "11:00 - 14:30"}
        ));
        legend.setAllRowsVisible(true);
        add(legend);
    }

    private void addShiftButton(HorizontalLayout layout, String label, String start, String end, 
                                ComboBox<Employee> empSel, DatePicker dateSel, ShiftRepository repo) {
        Button btn = new Button(label, e -> {
            if (empSel.getValue() == null || dateSel.getValue() == null) {
                Notification.show("Bitte Mitarbeiter und Datum wählen!");
                return;
            }

            LocalDateTime sTime = dateSel.getValue().atTime(LocalTime.parse(start));
            LocalDateTime eTime = dateSel.getValue().atTime(LocalTime.parse(end));

            if (repo.countOverlappingShifts(empSel.getValue(), sTime, eTime) > 0) {
                Notification n = Notification.show("Mitarbeiter hat bereits eine Schicht!");
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            } else {
                Shift s = new Shift();
                s.setLabel(label);
                s.setEmployee(empSel.getValue());
                s.setStartTime(sTime);
                s.setEndTime(eTime);
                repo.save(s);
                getUI().ifPresent(ui -> ui.getPage().reload());
            }
        });
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        layout.add(btn);
    }
}